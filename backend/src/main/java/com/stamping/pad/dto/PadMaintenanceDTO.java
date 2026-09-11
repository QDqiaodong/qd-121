package com.stamping.pad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PadMaintenanceDTO {

    @NotNull(message = "垫板ID不能为空")
    private Long padId;

    @NotBlank(message = "保养类型不能为空")
    private String maintenanceType;

    @NotBlank(message = "处理人不能为空")
    private String handler;

    private LocalDateTime maintenanceTime;

    @NotBlank(message = "保养结果不能为空")
    private String maintenanceResult;

    @NotBlank(message = "保养后状态不能为空")
    private String statusAfter;

    private String remark;
}
