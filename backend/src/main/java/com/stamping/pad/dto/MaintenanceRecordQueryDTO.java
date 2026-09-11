package com.stamping.pad.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MaintenanceRecordQueryDTO {

    private Long pageNum = 1L;

    private Long pageSize = 10L;

    /** 按垫板当前保养状态筛选：AVAILABLE/PENDING/DISABLED */
    private String status;

    private String padCode;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
