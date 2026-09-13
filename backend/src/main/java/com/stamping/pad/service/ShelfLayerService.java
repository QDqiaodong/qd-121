package com.stamping.pad.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.entity.LayerBlockRecord;
import com.stamping.pad.entity.LayerCapacityExpandRecord;
import com.stamping.pad.entity.PadInfo;
import com.stamping.pad.entity.PadInventorySheet;
import com.stamping.pad.entity.ShelfLayer;
import com.stamping.pad.mapper.LayerBlockRecordMapper;
import com.stamping.pad.mapper.LayerCapacityExpandRecordMapper;
import com.stamping.pad.mapper.PadInventorySheetMapper;
import com.stamping.pad.mapper.ShelfLayerMapper;
import com.stamping.pad.mapper.PadInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
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
    private final LayerCapacityExpandRecordMapper expandRecordMapper;
    private final PadInventorySheetMapper inventorySheetMapper;

    public List<ShelfLayer> listAll() {
        List<ShelfLayer> layers = shelfLayerMapper.selectAllWithCount();
        fillActiveBlock(layers);
        fillActiveExpand(layers);
        fillActiveUnbalanced(layers);
        return layers;
    }

    public Page<ShelfLayer> pageList(Long pageNum, Long pageSize) {
        Page<ShelfLayer> page = new Page<>(pageNum, pageSize);
        List<ShelfLayer> allLayers = shelfLayerMapper.selectAllWithCount();
        fillActiveBlock(allLayers);
        fillActiveExpand(allLayers);
        fillActiveUnbalanced(allLayers);
        int start = (int) ((pageNum - 1) * pageSize);
        int end = Math.min(start + pageSize.intValue(), allLayers.size());
        page.setRecords(allLayers.subList(start, end));
        page.setTotal(allLayers.size());
        return page;
    }

    public ShelfLayer getByCode(String layerCode) {
        ShelfLayer layer = shelfLayerMapper.selectByLayerCode(layerCode);
        fillActiveBlock(layer);
        fillActiveExpand(layer);
        fillActiveUnbalanced(layer);
        return layer;
    }

    public ShelfLayer getById(Long id) {
        ShelfLayer layer = shelfLayerMapper.selectById(id);
        if (layer != null) {
            layer.setPadList(padInfoMapper.selectByLayerCode(layer.getLayerCode()));
            fillActiveBlock(layer);
            fillActiveExpand(layer);
            fillActiveUnbalanced(layer);
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

    /** 回填层位当前生效的临时扩容记录与实际配额，前端按 activeExpand 标注“扩容中”并按新配额展示占用进度 */
    private void fillActiveExpand(List<ShelfLayer> layers) {
        if (layers == null || layers.isEmpty()) {
            return;
        }
        Map<String, LayerCapacityExpandRecord> activeMap = expandRecordMapper
                .selectEffectiveExpansions(LocalDateTime.now()).stream()
                .collect(Collectors.toMap(LayerCapacityExpandRecord::getLayerCode, Function.identity(), (a, b) -> a));
        layers.forEach(layer -> {
            LayerCapacityExpandRecord expand = activeMap.get(layer.getLayerCode());
            layer.setActiveExpand(expand);
            layer.setEffectiveCapacity(effectiveCapacityOf(layer, expand));
        });
    }

    private void fillActiveExpand(ShelfLayer layer) {
        if (layer == null) {
            return;
        }
        fillActiveExpand(List.of(layer));
    }

    /** 实际配额：扩容期内取扩容后配额，否则取层位基础配额 */
    private int effectiveCapacityOf(ShelfLayer layer, LayerCapacityExpandRecord activeExpand) {
        if (activeExpand != null && activeExpand.getExpandCapacity() != null) {
            return activeExpand.getExpandCapacity();
        }
        return layer.getCapacity() == null ? 0 : layer.getCapacity();
    }

    /** 回填覆盖本层的待闭环盘点单，前端按 activeUnbalanced 是否为空标注“未平账”并限制归还上架 */
    private void fillActiveUnbalanced(List<ShelfLayer> layers) {
        if (layers == null || layers.isEmpty()) {
            return;
        }
        Map<String, PadInventorySheet> unbalancedMap = new HashMap<>();
        for (PadInventorySheet sheet : inventorySheetMapper.selectUnbalanced()) {
            for (String code : sheet.getCoveredLayerList()) {
                unbalancedMap.putIfAbsent(code, sheet);
            }
        }
        layers.forEach(layer -> layer.setActiveUnbalanced(unbalancedMap.get(layer.getLayerCode())));
    }

    private void fillActiveUnbalanced(ShelfLayer layer) {
        if (layer == null) {
            return;
        }
        fillActiveUnbalanced(List.of(layer));
    }

    /**
     * 归还上架前校验：层位存在盘点差异未闭环（待闭环盘点单覆盖）时拒绝。
     * 仅归还入口调用（绑定/换绑/导入不受影响），闭环后自动恢复可归还。
     */
    public void assertNotUnbalancedForReturn(String layerCode) {
        for (PadInventorySheet sheet : inventorySheetMapper.selectUnbalanced()) {
            if (sheet.getCoveredLayerList().contains(layerCode)) {
                throw new RuntimeException("层位【" + layerCode + "】盘点差异未闭环（单号 "
                        + sheet.getSheetNo() + "：" + sheet.getDiffReason()
                        + "），未闭环前禁止归还上架，请先在交班盘点台账处理并闭环");
            }
        }
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
     * 上架前校验：行锁目标层位（须在事务内调用），层位不存在、封锁中或占用已达实际配额时拒绝。
     * 绑定、换绑、新建档案带层位、归还上架、批量导入统一走此入口，保证不超配额、不占封锁层。
     * 实际配额：扩容期内取临时扩容后的新配额，到期/提前结束后自动回到原配额。
     */
    public ShelfLayer lockAndAssertCapacity(String layerCode) {
        ShelfLayer layer = shelfLayerMapper.lockByLayerCode(layerCode);
        if (layer == null) {
            throw new RuntimeException("货架分层不存在");
        }
        assertNotBlocked(layerCode);
        int used = countOnShelf(layerCode);
        // 扩容记录与层位行锁串行化（登记扩容同锁层位行），此处读到的即当前生效口径
        LayerCapacityExpandRecord activeExpand =
                expandRecordMapper.selectEffectiveExpansion(layerCode, LocalDateTime.now());
        int capacity = effectiveCapacityOf(layer, activeExpand);
        if (used >= capacity) {
            throw new RuntimeException("层位【" + layerCode + "】已满（" + used + "/" + capacity + "），无法继续上架");
        }
        layer.setPadCount(used);
        layer.setActiveExpand(activeExpand);
        layer.setEffectiveCapacity(capacity);
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
            // 扩容中的层位须先结束扩容再删除，避免扩容台账留下无主记录
            Long expanding = expandRecordMapper.selectCount(
                    new LambdaQueryWrapper<LayerCapacityExpandRecord>()
                            .eq(LayerCapacityExpandRecord::getLayerCode, layer.getLayerCode())
                            .in(LayerCapacityExpandRecord::getStatus,
                                    LayerCapacityExpandRecord.STATUS_PENDING,
                                    LayerCapacityExpandRecord.STATUS_ACTIVE));
            if (expanding > 0) {
                throw new RuntimeException("该分层处于临时扩容中，请先结束扩容或待其到期后再删除");
            }
            shelfLayerMapper.deleteById(id);
        }
    }

    public List<ShelfLayer> listGroupByShelf() {
        List<ShelfLayer> layers = shelfLayerMapper.selectAllWithCount();
        fillActiveBlock(layers);
        fillActiveExpand(layers);
        fillActiveUnbalanced(layers);
        for (ShelfLayer layer : layers) {
            layer.setPadList(padInfoMapper.selectByLayerCode(layer.getLayerCode()));
        }
        return layers;
    }
}
