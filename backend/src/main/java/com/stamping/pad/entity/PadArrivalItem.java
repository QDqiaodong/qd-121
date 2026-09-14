package com.stamping.pad.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 到货明细：批内垫板清单。登记时创建垫板档案（待检层，无层位）；
 * 质检通过时记录每块垫板转入的正式层位，便于台账回看对照。
 */
@Data
@TableName("pad_arrival_item")
public class PadArrivalItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 到货批次ID */
    private Long batchId;

    /** 垫板ID */
    private Long padId;

    /** 垫板编号（登记时快照） */
    private String padCode;

    /** 适配模具（登记时快照） */
    private String moldType;

    /** 质检通过转入的正式层位（判定时记录） */
    private String targetLayerCode;

    private LocalDateTime createTime;

    /** 垫板当前库存状态（联表查询，非本表字段），台账回看对照用 */
    @TableField(exist = false)
    private String currentStockStatus;

    /** 垫板当前层位（联表查询，非本表字段），判定后实际上架层位对照用 */
    @TableField(exist = false)
    private String currentLayerCode;
}
