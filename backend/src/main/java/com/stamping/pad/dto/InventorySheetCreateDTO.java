package com.stamping.pad.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InventorySheetCreateDTO {

    /** LAYER-按层位、SHELF-按货架 */
    @NotBlank(message = "点检范围不能为空")
    private String scopeType;

    /** 按货架点检时必填 */
    private String shelfCode;

    /** 按层位点检时必填 */
    private String layerCode;

    /** DAY-白班、MIDDLE-中班、NIGHT-夜班 */
    @NotBlank(message = "班次不能为空")
    private String shift;

    @NotBlank(message = "点检人不能为空")
    private String inspector;

    private String remark;
}
