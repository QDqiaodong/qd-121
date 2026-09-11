package com.stamping.pad.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pad_inventory_sheet")
public class PadInventorySheet {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 盘点单号：PD + 日期时间 + 随机串 */
    private String sheetNo;

    /** LAYER-按层位、SHELF-按货架 */
    private String scopeType;

    /** 货架编码（按货架点检时覆盖该货架全部层位） */
    private String shelfCode;

    /** 层位编码（按层位点检时的目标层） */
    private String layerCode;

    /** 覆盖层位编码，多个用逗号分隔，便于重叠校验与展示 */
    private String coveredLayers;

    /** 班次：DAY-白班、MIDDLE-中班、NIGHT-夜班 */
    private String shift;

    /** 点检人 */
    private String inspector;

    /** IN_PROGRESS-点检中、SUBMITTED-已提交、CANCELLED-已取消 */
    private String status;

    /** 开单时间 */
    private LocalDateTime startTime;

    /** 提交时间（取消的单据不留痕，始终为空） */
    private LocalDateTime submitTime;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ---------------- 联表/聚合字段（非本表字段） ----------------

    /** 覆盖层位数 */
    @TableField(exist = false)
    private Integer layerCount;

    /** 明细总数 */
    @TableField(exist = false)
    private Integer totalCount;

    /** 账实相符数 */
    @TableField(exist = false)
    private Integer matchCount;

    /** 缺失数 */
    @TableField(exist = false)
    private Integer missingCount;

    /** 多出数 */
    @TableField(exist = false)
    private Integer extraCount;

    /** 已标记数（多出明细不参与逐块标记） */
    @TableField(exist = false)
    private Integer markedCount;
}
