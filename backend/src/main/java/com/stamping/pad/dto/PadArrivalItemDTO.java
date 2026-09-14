package com.stamping.pad.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 到货登记明细行：每行为一块新到垫板，登记时创建档案并落入待检层。
 */
@Data
public class PadArrivalItemDTO {

    /** 垫板编号（必填，批次内与档案库均不可重复） */
    private String padCode;

    private String moldType;

    private BigDecimal length;

    private BigDecimal width;

    private BigDecimal thickness;

    private String remark;
}
