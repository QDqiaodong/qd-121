package com.stamping.pad.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("layer_capacity_expand_record")
public class LayerCapacityExpandRecord {

    /** 待生效：生效开始时间未到，层位仍按原配额校验 */
    public static final String STATUS_PENDING = "PENDING";
    /** 生效中：绑定/换绑/归还上架/导入按扩容后配额校验 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 已到期：超过生效结束时间，系统自动回到原配额 */
    public static final String STATUS_EXPIRED = "EXPIRED";
    /** 已结束：未到期提前结束，登记结束结论 */
    public static final String STATUS_ENDED = "ENDED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long layerId;

    private String layerCode;

    /** 扩容原因：如旺季到货、集中入库 */
    private String expandReason;

    /** 原配额（登记时快照，结束后回到该配额） */
    private Integer originalCapacity;

    /** 扩容后配额（须大于原配额） */
    private Integer expandCapacity;

    private LocalDateTime startTime;

    /** 生效结束时间：到期自动回到原配额 */
    private LocalDateTime endTime;

    /** 登记扩容的经办人 */
    private String operator;

    /** PENDING-待生效、ACTIVE-生效中、EXPIRED-已到期、ENDED-已结束 */
    private String status;

    /** 实际结束时间：提前结束时间或到期时间 */
    private LocalDateTime finishTime;

    /** 结束结论：提前结束必填，到期由系统补写 */
    private String finishConclusion;

    private String finishOperator;

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
