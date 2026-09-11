package com.stamping.pad.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.entity.PadInfo;
import com.stamping.pad.entity.ShelfLayer;
import com.stamping.pad.mapper.ShelfLayerMapper;
import com.stamping.pad.mapper.PadInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShelfLayerService {

    /** 新建分层的默认容量配额 */
    public static final int DEFAULT_CAPACITY = 10;

    private final ShelfLayerMapper shelfLayerMapper;
    private final PadInfoMapper padInfoMapper;

    public List<ShelfLayer> listAll() {
        return shelfLayerMapper.selectAllWithCount();
    }

    public Page<ShelfLayer> pageList(Long pageNum, Long pageSize) {
        Page<ShelfLayer> page = new Page<>(pageNum, pageSize);
        List<ShelfLayer> allLayers = shelfLayerMapper.selectAllWithCount();
        int start = (int) ((pageNum - 1) * pageSize);
        int end = Math.min(start + pageSize.intValue(), allLayers.size());
        page.setRecords(allLayers.subList(start, end));
        page.setTotal(allLayers.size());
        return page;
    }

    public ShelfLayer getByCode(String layerCode) {
        return shelfLayerMapper.selectByLayerCode(layerCode);
    }

    public ShelfLayer getById(Long id) {
        ShelfLayer layer = shelfLayerMapper.selectById(id);
        if (layer != null) {
            layer.setPadList(padInfoMapper.selectByLayerCode(layer.getLayerCode()));
        }
        return layer;
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
     * 上架前容量校验：行锁目标层位（须在事务内调用），层位不存在或占用已达配额时拒绝。
     * 绑定、换绑、新建档案带层位、归还上架、批量导入统一走此入口，保证不超配额。
     */
    public ShelfLayer lockAndAssertCapacity(String layerCode) {
        ShelfLayer layer = shelfLayerMapper.lockByLayerCode(layerCode);
        if (layer == null) {
            throw new RuntimeException("货架分层不存在");
        }
        int used = countOnShelf(layerCode);
        int capacity = layer.getCapacity() == null ? 0 : layer.getCapacity();
        if (used >= capacity) {
            throw new RuntimeException("层位【" + layerCode + "】已满（" + used + "/" + capacity + "），无法继续上架");
        }
        layer.setPadCount(used);
        return layer;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ShelfLayer layer = shelfLayerMapper.selectById(id);
        if (layer != null) {
            int count = padInfoMapper.selectByLayerCode(layer.getLayerCode()).size();
            if (count > 0) {
                throw new RuntimeException("该分层下存在垫板，无法删除");
            }
            shelfLayerMapper.deleteById(id);
        }
    }

    public List<ShelfLayer> listGroupByShelf() {
        List<ShelfLayer> layers = shelfLayerMapper.selectAllWithCount();
        for (ShelfLayer layer : layers) {
            layer.setPadList(padInfoMapper.selectByLayerCode(layer.getLayerCode()));
        }
        return layers;
    }
}
