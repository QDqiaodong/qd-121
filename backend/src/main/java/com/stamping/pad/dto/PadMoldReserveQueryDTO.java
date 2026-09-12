package com.stamping.pad.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PadMoldReserveQueryDTO {

    private Long pageNum = 1L;

    private Long pageSize = 10L;

    /** 按预留状态筛选：PENDING-待生效、ACTIVE-生效中、EXPIRED-已到期、RELEASED-已释放 */
    private String status;

    /** 按模具编码/名称模糊筛选 */
    private String moldCode;

    /** 按垫板编码筛选（明细表关联） */
    private String padCode;

    private String operator;

    /** 按预留开始时间区间筛选 */
    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
