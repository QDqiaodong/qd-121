package com.stamping.pad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BindLayerDTO {

    @NotNull(message = "垫板ID不能为空")
    private Long padId;

    @NotBlank(message = "货架分层编码不能为空")
    private String layerCode;

    private String operator;

    private String adjustReason;
}
