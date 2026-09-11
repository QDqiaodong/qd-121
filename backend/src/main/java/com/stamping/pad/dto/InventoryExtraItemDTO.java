package com.stamping.pad.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InventoryExtraItemDTO {

    /** 多出垫板所在层位，必须属于本单覆盖范围 */
    @NotBlank(message = "多出垫板所在层位不能为空")
    private String layerCode;

    @NotBlank(message = "垫板编号不能为空")
    private String padCode;

    private String moldType;

    private String remark;
}
