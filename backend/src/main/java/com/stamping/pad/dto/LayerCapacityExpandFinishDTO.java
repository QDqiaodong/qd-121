package com.stamping.pad.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 提前结束层位临时扩容：必须填写结束结论，结束后立即回到原配额 */
@Data
public class LayerCapacityExpandFinishDTO {

    /** 扩容记录ID */
    private Long id;

    /** 结束结论（必填） */
    private String finishConclusion;

    /** 结束经办人，缺省取登记经办人 */
    private String finishOperator;

    /** 结束时间，默认当前时间 */
    private LocalDateTime finishTime;
}
