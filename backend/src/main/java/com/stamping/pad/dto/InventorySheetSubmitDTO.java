package com.stamping.pad.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InventorySheetSubmitDTO {

    @NotNull(message = "盘点单不存在")
    private Long sheetId;

    /** 差异原因：存在缺失/多出差异时必填，未闭环前在概览/层位页/归还弹窗展示 */
    private String diffReason;
}
