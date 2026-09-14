package com.stamping.pad.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stamping.pad.entity.PadArrivalItem;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

public interface PadArrivalItemMapper extends BaseMapper<PadArrivalItem> {

    List<PadArrivalItem> selectByBatchId(@Param("batchId") Long batchId);

    List<PadArrivalItem> selectByBatchIds(@Param("batchIds") Collection<Long> batchIds);
}
