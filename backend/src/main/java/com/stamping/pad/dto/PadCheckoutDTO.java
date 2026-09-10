package com.stamping.pad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PadCheckoutDTO {

    @NotNull(message = "垫板ID不能为空")
    private Long padId;

    @NotBlank(message = "领用人不能为空")
    private String borrower;

    @NotBlank(message = "产线/工位不能为空")
    private String productionLine;

    private String purpose;

    private LocalDateTime checkoutTime;

    private LocalDateTime expectedReturnTime;

    private String remark;
}
