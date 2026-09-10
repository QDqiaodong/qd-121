package com.stamping.pad.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BorrowRecordQueryDTO {

    private Long pageNum = 1L;

    private Long pageSize = 10L;

    /** BORROWED-领用中 RETURNED-已归还 OVERDUE-已逾期 */
    private String status;

    private String padCode;

    private String borrower;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
