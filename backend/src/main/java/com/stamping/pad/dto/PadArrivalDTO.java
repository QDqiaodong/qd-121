package com.stamping.pad.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 到货登记：新到垫板先落待检层，登记到货批次、到货时间、经办人与批内垫板清单。
 */
@Data
public class PadArrivalDTO {

    /** 到货时间（缺省取当前时间） */
    private LocalDateTime arrivalTime;

    /** 经办人（必填） */
    private String operator;

    /** 供应商/来源（可选） */
    private String supplier;

    private String remark;

    /** 批内垫板清单（至少一块） */
    private List<PadArrivalItemDTO> items;
}
