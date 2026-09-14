package com.stamping.pad.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 判退离库：整批判定离库，批内垫板档案冻结（stock_status=REJECTED）；判退结论必填。
 */
@Data
public class PadArrivalRejectDTO {

    /** 批次ID */
    private Long id;

    /** 质检时间（缺省取当前时间） */
    private LocalDateTime inspectTime;

    /** 质检人（缺省取登记经办人） */
    private String inspector;

    /** 判退结论/原因（必填） */
    private String inspectConclusion;

    private String remark;
}
