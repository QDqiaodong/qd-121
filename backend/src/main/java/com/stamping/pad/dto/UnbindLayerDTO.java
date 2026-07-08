package com.stamping.pad.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UnbindLayerDTO {

    @NotNull(message = "垫板ID不能为空")
    private Long padId;

    private String operator;

    private String adjustReason;
}
