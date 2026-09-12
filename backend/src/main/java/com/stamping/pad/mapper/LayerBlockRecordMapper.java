package com.stamping.pad.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.dto.LayerBlockRecordQueryDTO;
import com.stamping.pad.entity.LayerBlockRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface LayerBlockRecordMapper extends BaseMapper<LayerBlockRecord> {

    Page<LayerBlockRecord> selectPageList(Page<LayerBlockRecord> page, @Param("query") LayerBlockRecordQueryDTO query);

    /** 全部“封锁中”记录：用于层位列表/下拉标注封锁状态与上架前校验 */
    List<LayerBlockRecord> selectActiveBlocks();
}
