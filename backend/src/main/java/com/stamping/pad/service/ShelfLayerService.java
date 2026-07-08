package com.stamping.pad.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
            shelfLayerMapper.insert(shelfLayer);
        } else {
            shelfLayerMapper.updateById(shelfLayer);
        }
        return shelfLayer;
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
