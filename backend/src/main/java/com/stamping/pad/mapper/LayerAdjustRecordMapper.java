package com.stamping.pad.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.dto.RecordQueryDTO;
import com.stamping.pad.entity.LayerAdjustRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface LayerAdjustRecordMapper extends BaseMapper<LayerAdjustRecord> {

    Page<LayerAdjustRecord> selectPageList(Page<LayerAdjustRecord> page, @Param("query") RecordQueryDTO query);

    List<LayerAdjustRecord> selectByPadId(@Param("padId") Long padId);
}
