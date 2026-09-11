package com.stamping.pad.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stamping.pad.entity.PadInventoryItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PadInventoryItemMapper extends BaseMapper<PadInventoryItem> {

    /** 点检明细：联表层位名称与垫板实时在架层/状态，档案与多出明细分开排序 */
    List<PadInventoryItem> selectDetailBySheetId(@Param("sheetId") Long sheetId);

    /** 同单同层同编号的多出明细查重（不区分大小写，避免重复补录） */
    PadInventoryItem selectExtraDuplicate(@Param("sheetId") Long sheetId,
                                          @Param("layerCode") String layerCode,
                                          @Param("padCode") String padCode);
}
