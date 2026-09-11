package com.stamping.pad.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.dto.InventorySheetQueryDTO;
import com.stamping.pad.entity.PadInventorySheet;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PadInventorySheetMapper extends BaseMapper<PadInventorySheet> {

    Page<PadInventorySheet> selectPageList(Page<PadInventorySheet> page,
                                           @Param("query") InventorySheetQueryDTO query);

    /** 点检中单据：创建时按覆盖层位做重叠校验 */
    List<PadInventorySheet> selectInProgress();
}
