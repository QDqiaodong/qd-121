package com.stamping.pad.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LayerBlockRecordQueryDTO {

    private Long pageNum = 1L;

    private Long pageSize = 10L;

    /** 按封锁状态筛选：BLOCKED-封锁中、RELEASED-已解除 */
    private String status;

    private String layerCode;

    /** 按封锁类型筛选：DAMAGE/CLEANING/MAINTENANCE/OTHER */
    private String blockType;

    /** 按封锁开始时间区间筛选 */
    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
