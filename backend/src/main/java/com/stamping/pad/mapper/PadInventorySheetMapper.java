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

    /** 盘点中单据：创建时按覆盖层位做重叠校验 */
    List<PadInventorySheet> selectInProgress();

    /** 待闭环（有差异未平账）单据：层位列表/归还校验据此标注未平账并禁止归还上架 */
    List<PadInventorySheet> selectUnbalanced();

    /**
     * 已闭环的差异单据（有差异原因、按闭环时间倒序）：
     * 层位列表据此回填每层最近一次闭环摘要（处理结论/处理人），未平账单优先于历史闭环展示。
     */
    List<PadInventorySheet> selectLastClosedDiffSheets();
}
