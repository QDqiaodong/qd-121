package com.stamping.pad.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 换模垫板预留登记：模具、预留板清单、生效时段与经办人 */
@Data
public class PadMoldReserveDTO {

    /** 即将上线的模具编码（必填） */
    private String moldCode;

    /** 模具名称（可选） */
    private String moldName;

    /** 上线产线/工位（可选） */
    private String productionLine;

    /** 预留垫板ID清单（必填，至少一块，登记时须在架且可用） */
    private List<Long> padIds;

    /** 预留开始时间，默认当前时间 */
    private LocalDateTime startTime;

    /** 预留结束时间（必填，到期自动释放） */
    private LocalDateTime endTime;

    /** 经办人（必填） */
    private String operator;

    private String remark;
}
