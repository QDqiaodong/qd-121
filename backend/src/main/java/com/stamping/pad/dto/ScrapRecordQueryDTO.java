package com.stamping.pad.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ScrapRecordQueryDTO {

    private Long pageNum = 1L;

    private Long pageSize = 10L;

    private String padCode;

    /** 报废去向 */
    private String destination;

    /** 批准人 */
    private String approver;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
