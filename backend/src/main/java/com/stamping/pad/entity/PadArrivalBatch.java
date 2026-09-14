package com.stamping.pad.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 到货批次：新到垫板先落待检层，登记到货批次、到货时间与经办人。
 * 待检层垫板（stock_status=QUARANTINE）不可领用、换绑或计入可用库存；
 * 质检通过整批转正式层，判退整批离库（stock_status=REJECTED）。
 */
@Data
@TableName("pad_arrival_batch")
public class PadArrivalBatch {

    /** 待检中：批次已登记未判定，批内垫板冻结在待检层 */
    public static final String STATUS_PENDING = "PENDING";
    /** 质检通过：整批转正式层，垫板恢复为正式在库 */
    public static final String STATUS_PASSED = "PASSED";
    /** 判退离库：整批离库，垫板档案冻结留存 */
    public static final String STATUS_REJECTED = "REJECTED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 到货批次号：DH + 日期时间 + 随机串，系统生成 */
    private String batchNo;

    /** 供应商/来源 */
    private String supplier;

    /** 到货时间 */
    private LocalDateTime arrivalTime;

    /** 经办人 */
    private String operator;

    /** 批次垫板块数（登记时快照） */
    private Integer padCount;

    /** PENDING-待检中、PASSED-质检通过、REJECTED-判退离库 */
    private String status;

    /** 质检判定时间 */
    private LocalDateTime inspectTime;

    /** 质检人（缺省取经办人） */
    private String inspector;

    /** 质检结论（通过/判退时必填） */
    private String inspectConclusion;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 批内垫板清单（联表查询，非本表字段） */
    @TableField(exist = false)
    private List<PadArrivalItem> items;
}
