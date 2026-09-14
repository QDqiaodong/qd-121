package com.stamping.pad.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PadArrivalQueryDTO {

    private Long pageNum = 1L;

    private Long pageSize = 10L;

    /** 批次状态：PENDING-待检中、PASSED-质检通过、REJECTED-判退离库 */
    private String status;

    private String batchNo;

    /** 按批内垫板编号筛选 */
    private String padCode;

    private String operator;

    /** 到货时间区间（起） */
    private LocalDateTime startTime;

    /** 到货时间区间（止） */
    private LocalDateTime endTime;
}
