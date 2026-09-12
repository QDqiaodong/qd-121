package com.stamping.pad.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LayerCapacityExpandQueryDTO {

    private Long pageNum = 1L;

    private Long pageSize = 10L;

    /** 按生效状态筛选：PENDING-待生效、ACTIVE-生效中、EXPIRED-已到期、ENDED-已结束 */
    private String status;

    private String layerCode;

    /** 按生效开始时间区间筛选 */
    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
