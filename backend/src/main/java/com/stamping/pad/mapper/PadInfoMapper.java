package com.stamping.pad.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.dto.PadQueryDTO;
import com.stamping.pad.entity.PadInfo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PadInfoMapper extends BaseMapper<PadInfo> {

    Page<PadInfo> selectPageList(Page<PadInfo> page, @Param("query") PadQueryDTO query);

    List<PadInfo> selectByLayerCode(@Param("layerCode") String layerCode);

    List<PadInfo> selectByShelfCode(@Param("shelfCode") String shelfCode);
}
