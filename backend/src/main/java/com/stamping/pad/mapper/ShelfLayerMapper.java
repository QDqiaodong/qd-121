package com.stamping.pad.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stamping.pad.entity.ShelfLayer;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ShelfLayerMapper extends BaseMapper<ShelfLayer> {

    List<ShelfLayer> selectAllWithCount();

    ShelfLayer selectByLayerCode(@Param("layerCode") String layerCode);

    /** 按分层编码行锁查询（SELECT ... FOR UPDATE），须在事务内调用 */
    ShelfLayer lockByLayerCode(@Param("layerCode") String layerCode);
}
