package com.stamping.pad.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 层位封锁登记：破损/待清扫/检修时登记原因、开始时间与经办人 */
@Data
public class LayerBlockDTO {

    /** 目标分层编码 */
    private String layerCode;

    /** DAMAGE-层位破损、CLEANING-待清扫、MAINTENANCE-检修中、OTHER-其他 */
    private String blockType;

    /** 封锁原因（必填） */
    private String blockReason;

    /** 封锁开始时间，默认当前时间 */
    private LocalDateTime startTime;

    /** 经办人（必填） */
    private String operator;

    private String remark;
}
