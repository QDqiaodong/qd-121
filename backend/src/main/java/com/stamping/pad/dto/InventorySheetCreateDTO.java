package com.stamping.pad.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InventorySheetCreateDTO {

    /** LAYER-按层位、SHELF-按货架 */
    @NotBlank(message = "盘点范围不能为空")
    private String scopeType;

    /** 按货架盘点时必填 */
    private String shelfCode;

    /** 按层位盘点时必填 */
    private String layerCode;

    /** DAY-白班、MIDDLE-中班、NIGHT-夜班 */
    @NotBlank(message = "班次不能为空")
    private String shift;

    @NotBlank(message = "盘点人不能为空")
    private String inspector;

    private String remark;
}
