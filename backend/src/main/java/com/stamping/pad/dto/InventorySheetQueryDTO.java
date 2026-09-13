package com.stamping.pad.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InventorySheetQueryDTO {

    private Long pageNum = 1L;

    private Long pageSize = 10L;

    /** IN_PROGRESS-盘点中、SUBMITTED-待闭环、CLOSED-已闭环、CANCELLED-已取消 */
    private String status;

    /** DAY/MIDDLE/NIGHT */
    private String shift;

    private String shelfCode;

    /** 按覆盖层位精确匹配（含按货架盘点覆盖到该层的单据） */
    private String layerCode;

    /** 单号模糊查询 */
    private String sheetNo;

    /** 开单时间区间（回看历史按日期筛选） */
    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
