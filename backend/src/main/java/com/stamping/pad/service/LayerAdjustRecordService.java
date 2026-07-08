package com.stamping.pad.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.dto.RecordQueryDTO;
import com.stamping.pad.entity.LayerAdjustRecord;
import com.stamping.pad.mapper.LayerAdjustRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LayerAdjustRecordService {

    private final LayerAdjustRecordMapper recordMapper;

    public Page<LayerAdjustRecord> pageList(RecordQueryDTO query) {
        Page<LayerAdjustRecord> page = new Page<>(query.getPageNum(), query.getPageSize());
        return recordMapper.selectPageList(page, query);
    }

    public List<LayerAdjustRecord> listByPadId(Long padId) {
        return recordMapper.selectByPadId(padId);
    }

    public LayerAdjustRecord getById(Long id) {
        return recordMapper.selectById(id);
    }
}
