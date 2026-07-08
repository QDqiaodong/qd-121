package com.stamping.pad.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PadInfoDTO {

    private Long id;

    @NotBlank(message = "垫板编号不能为空")
    private String padCode;

    private String moldType;

    private BigDecimal length;

    private BigDecimal width;

    private BigDecimal thickness;

    private String imagePath;

    private String shelfLayerCode;

    private String remark;
}
