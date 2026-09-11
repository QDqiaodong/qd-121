package com.stamping.pad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InventoryItemMarkDTO {

    @NotNull(message = "点检明细不存在")
    private Long itemId;

    /** MATCH-账实相符、MISSING-缺失（多出明细固定 EXTRA，不在此标记） */
    @NotBlank(message = "点检结果不能为空")
    private String checkResult;

    private String remark;
}
