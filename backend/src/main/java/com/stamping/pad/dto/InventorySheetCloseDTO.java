package com.stamping.pad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InventorySheetCloseDTO {

    @NotNull(message = "盘点单不存在")
    private Long sheetId;

    /** 处理结论：差异如何处理完毕（如缺失板已找回上架/多出板已归位），必填 */
    @NotBlank(message = "闭环必须填写处理结论")
    private String closeConclusion;

    /** 闭环时间，缺省取当前时间，不能早于提交时间 */
    private LocalDateTime closeTime;

    /** 处理人：差异处理闭环的经办人，必填（不再缺省取盘点人） */
    @NotBlank(message = "闭环必须填写处理人")
    private String closeOperator;
}
