package com.stamping.pad.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.dto.PadArrivalQueryDTO;
import com.stamping.pad.entity.PadArrivalBatch;
import org.apache.ibatis.annotations.Param;

public interface PadArrivalBatchMapper extends BaseMapper<PadArrivalBatch> {

    Page<PadArrivalBatch> selectPageList(Page<PadArrivalBatch> page, @Param("query") PadArrivalQueryDTO query);

    PadArrivalBatch selectDetailById(@Param("id") Long id);

    /** 质检判定前对批次行加锁：串行化同一批次的通过/判退并发 */
    PadArrivalBatch lockById(@Param("id") Long id);
}
