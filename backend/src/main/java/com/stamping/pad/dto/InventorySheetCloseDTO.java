package com.stamping.pad.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InventorySheetCloseDTO {

    @NotNull(message = "盘点单不存在")
    private Long sheetId;

    /** 闭环结论：差异如何处理完毕（如缺失板已找回上架/多出板已归位），必填 */
    private String closeConclusion;

    /** 闭环时间，缺省取当前时间，不能早于提交时间 */
    private LocalDateTime closeTime;

    /** 闭环人，缺省取盘点人 */
    private String closeOperator;
}
