package com.stamping.pad.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RecordQueryDTO {

    private Long pageNum = 1L;

    private Long pageSize = 20L;

    private String padCode;

    private String adjustType;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
