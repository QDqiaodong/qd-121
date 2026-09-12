package com.stamping.pad.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.entity.LayerBlockRecord;
import com.stamping.pad.entity.PadInfo;
import com.stamping.pad.entity.ShelfLayer;
import com.stamping.pad.mapper.LayerBlockRecordMapper;
import com.stamping.pad.mapper.ShelfLayerMapper;
import com.stamping.pad.mapper.PadInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShelfLayerService {

    /** 新建分层的默认容量配额 */
    public static final int DEFAULT_CAPACITY = 10;

    private final ShelfLayerMapper shelfLayerMapper;
    private final PadInfoMapper padInfoMapper;
    private final LayerBlockRecordMapper layerBlockRecordMapper;

    public List<ShelfLayer> listAll() {
        List<ShelfLayer> layers = shelfLayerMapper.selectAllWithCount();
        fillActiveBlock(layers);
        return layers;
    }

    public Page<ShelfLayer> pageList(Long pageNum, Long pageSize) {
        Page<ShelfLayer> page = new Page<>(pageNum, pageSize);
        List<ShelfLayer> allLayers = shelfLayerMapper.selectAllWithCount();
        fillActiveBlock(allLayers);
        int start = (int) ((pageNum - 1) * pageSize);
        int end = Math.min(start + pageSize.intValue(), allLayers.size());
        page.setRecords(allLayers.subList(start, end));
        page.setTotal(allLayers.size());
        return page;
    }

    public ShelfLayer getByCode(String layerCode) {
        ShelfLayer layer = shelfLayerMapper.selectByLayerCode(layerCode);
        fillActiveBlock(layer);
        return layer;
    }

    public ShelfLayer getById(Long id) {
        ShelfLayer layer = shelfLayerMapper.selectById(id);
        if (layer != null) {
            layer.setPadList(padInfoMapper.selectByLayerCode(layer.getLayerCode()));
            fillActiveBlock(layer);
        }
        return layer;
    }

    /** 回填层位当前生效的封锁记录，前端按 activeBlock 是否为空标注“封锁中”并限制可选范围 */
    private void fillActiveBlock(List<ShelfLayer> layers) {
        if (layers == null || layers.isEmpty()) {
            return;
        }
        Map<String, LayerBlockRecord> activeMap = layerBlockRecordMapper.selectActiveBlocks().stream()
                .collect(Collectors.toMap(LayerBlockRecord::getLayerCode, Function.identity(), (a, b) -> a));
        layers.forEach(layer -> layer.setActiveBlock(activeMap.get(layer.getLayerCode())));
    }

    private void fillActiveBlock(ShelfLayer layer) {
        if (layer == null) {
            return;
        }
        fillActiveBlock(List.of(layer));
    }

    @Transactional(rollbackFor = Exception.class)
    public ShelfLayer save(ShelfLayer shelfLayer) {
        if (shelfLayer.getId() == null) {
            if (shelfLayer.getCapacity() == null) {
                shelfLayer.setCapacity(DEFAULT_CAPACITY);
            }
            assertCapacityValue(shelfLayer.getCapacity());
            shelfLayerMapper.insert(shelfLayer);
        } else {
            if (shelfLayer.getCapacity() != null) {
                assertCapacityValue(shelfLayer.getCapacity());
                ShelfLayer existing = shelfLayerMapper.selectById(shelfLayer.getId());
                if (existing == null) {
                    throw new RuntimeException("货架分层不存在");
                }
                // 下调配额不得低于当前在架数，否则已在架垫板会超出配额
                int used = countOnShelf(existing.getLayerCode());
                if (shelfLayer.getCapacity() < used) {
                    throw new RuntimeException("容量配额不能低于当前在架数（" + used + "块），请先移出部分垫板");
                }
            }
            shelfLayerMapper.updateById(shelfLayer);
        }
        return shelfLayer;
    }

    private void assertCapacityValue(Integer capacity) {
        if (capacity == null || capacity < 0) {
            throw new RuntimeException("容量配额必须为不小于 0 的整数");
        }
    }

    /** 当前在架数：以 pad_info.shelf_layer_code 实时统计，与层位占用展示口径一致 */
    public int countOnShelf(String layerCode) {
        return Math.toIntExact(padInfoMapper.selectCount(
                new LambdaQueryWrapper<PadInfo>().eq(PadInfo::getShelfLayerCode, layerCode)));
    }

    /**
     * 上架前校验：行锁目标层位（须在事务内调用），层位不存在、封锁中或占用已达配额时拒绝。
     * 绑定、换绑、新建档案带层位、归还上架、批量导入统一走此入口，保证不超配额、不占封锁层。
     */
    public ShelfLayer lockAndAssertCapacity(String layerCode) {
        ShelfLayer layer = shelfLayerMapper.lockByLayerCode(layerCode);
        if (layer == null) {
            throw new RuntimeException("货架分层不存在");
        }
        assertNotBlocked(layerCode);
        int used = countOnShelf(layerCode);
        int capacity = layer.getCapacity() == null ? 0 : layer.getCapacity();
        if (used >= capacity) {
            throw new RuntimeException("层位【" + layerCode + "】已满（" + used + "/" + capacity + "），无法继续上架");
        }
        layer.setPadCount(used);
        return layer;
    }

    /** 封锁中的层位禁止上架占位（绑定/换绑/归还/导入），解除封锁后自动恢复 */
    public void assertNotBlocked(String layerCode) {
        Long blocked = layerBlockRecordMapper.selectCount(
                new LambdaQueryWrapper<LayerBlockRecord>()
                        .eq(LayerBlockRecord::getLayerCode, layerCode)
                        .eq(LayerBlockRecord::getStatus, LayerBlockRecord.STATUS_BLOCKED));
        if (blocked > 0) {
            throw new RuntimeException("层位【" + layerCode + "】处于封锁中（破损/清扫/检修），封锁期间禁止上架占位，请先解除封锁");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ShelfLayer layer = shelfLayerMapper.selectById(id);
        if (layer != null) {
            int count = padInfoMapper.selectByLayerCode(layer.getLayerCode()).size();
            if (count > 0) {
                throw new RuntimeException("该分层下存在垫板，无法删除");
            }
            // 封锁中的层位须先解除封锁再删除，避免封锁台账留下无主记录
            Long blocked = layerBlockRecordMapper.selectCount(
                    new LambdaQueryWrapper<LayerBlockRecord>()
                            .eq(LayerBlockRecord::getLayerCode, layer.getLayerCode())
                            .eq(LayerBlockRecord::getStatus, LayerBlockRecord.STATUS_BLOCKED));
            if (blocked > 0) {
                throw new RuntimeException("该分层处于封锁中，请先解除封锁后再删除");
            }
            shelfLayerMapper.deleteById(id);
        }
    }

    public List<ShelfLayer> listGroupByShelf() {
        List<ShelfLayer> layers = shelfLayerMapper.selectAllWithCount();
        fillActiveBlock(layers);
        for (ShelfLayer layer : layers) {
            layer.setPadList(padInfoMapper.selectByLayerCode(layer.getLayerCode()));
        }
        return layers;
    }
}
