package com.stamping.pad.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 手工释放换模垫板预留：必须填写释放结论，释放后垫板立即恢复可领 */
@Data
public class PadMoldReserveReleaseDTO {

    /** 预留记录ID */
    private Long id;

    /** 释放结论（必填） */
    private String releaseConclusion;

    /** 释放经办人，缺省取登记经办人 */
    private String releaseOperator;

    /** 释放时间，默认当前时间 */
    private LocalDateTime releaseTime;
}
