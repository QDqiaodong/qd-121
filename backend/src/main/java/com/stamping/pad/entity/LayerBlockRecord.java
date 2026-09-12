package com.stamping.pad.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("layer_block_record")
public class LayerBlockRecord {

    /** 封锁中：该层禁止绑定/换绑/归还上架/导入占位 */
    public static final String STATUS_BLOCKED = "BLOCKED";
    /** 已解除：层位恢复可上架 */
    public static final String STATUS_RELEASED = "RELEASED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long layerId;

    private String layerCode;

    /** DAMAGE-层位破损、CLEANING-待清扫、MAINTENANCE-检修中、OTHER-其他 */
    private String blockType;

    private String blockReason;

    private LocalDateTime startTime;

    /** 登记封锁的经办人 */
    private String operator;

    /** BLOCKED-封锁中、RELEASED-已解除 */
    private String status;

    private LocalDateTime releaseTime;

    /** 解除结论：解除封锁时必填 */
    private String releaseConclusion;

    private String releaseOperator;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 分层名称（联表查询，非本表字段） */
    @TableField(exist = false)
    private String layerName;

    /** 货架编码（联表查询，非本表字段） */
    @TableField(exist = false)
    private String shelfCode;
}
