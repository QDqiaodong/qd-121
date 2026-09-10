package com.stamping.pad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PadReturnDTO {

    @NotNull(message = "领用记录ID不能为空")
    private Long id;

    @NotBlank(message = "归还层位不能为空")
    private String returnLayerCode;

    private LocalDateTime returnTime;

    private String operator;

    private String remark;
}
