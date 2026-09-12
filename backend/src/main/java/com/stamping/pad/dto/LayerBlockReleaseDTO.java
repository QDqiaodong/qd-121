package com.stamping.pad.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 解除层位封锁：必须填写解除结论 */
@Data
public class LayerBlockReleaseDTO {

    /** 封锁记录ID */
    private Long id;

    /** 解除结论（必填） */
    private String releaseConclusion;

    /** 解除经办人，缺省取登记经办人 */
    private String releaseOperator;

    /** 解除时间，默认当前时间 */
    private LocalDateTime releaseTime;
}
