package com.stamping.pad.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 层位临时扩容登记：旺季到货等原因时登记新配额、生效时段与经办人 */
@Data
public class LayerCapacityExpandDTO {

    /** 目标分层编码 */
    private String layerCode;

    /** 扩容原因（必填） */
    private String expandReason;

    /** 扩容后配额（必填，须大于原配额） */
    private Integer expandCapacity;

    /** 生效开始时间，默认当前时间 */
    private LocalDateTime startTime;

    /** 生效结束时间（必填），到期自动回到原配额 */
    private LocalDateTime endTime;

    /** 经办人（必填） */
    private String operator;

    private String remark;
}
