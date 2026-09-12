package com.stamping.pad.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 换模垫板预留：换模前库房把指定在架垫板预留给即将上线的模具。
 * 预留期内（生效时段内）这些垫板不能被其他产线领用，也不能解绑/换层；
 * 到期或手工释放后恢复可领。
 */
@Data
@TableName("pad_mold_reserve_record")
public class PadMoldReserveRecord {

    /** 待生效：生效开始时间未到，垫板仍可领用/解绑 */
    public static final String STATUS_PENDING = "PENDING";
    /** 生效中：预留板禁止领用、解绑与换层 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 已到期：超过生效结束时间，系统自动释放 */
    public static final String STATUS_EXPIRED = "EXPIRED";
    /** 已释放：未到期手工释放，登记释放结论 */
    public static final String STATUS_RELEASED = "RELEASED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 即将上线的模具编码 */
    private String moldCode;

    private String moldName;

    /** 上线产线/工位 */
    private String productionLine;

    private LocalDateTime startTime;

    /** 预留结束时间：到期自动释放，垫板恢复可领 */
    private LocalDateTime endTime;

    /** 登记预留的经办人 */
    private String operator;

    /** PENDING-待生效、ACTIVE-生效中、EXPIRED-已到期、RELEASED-已释放 */
    private String status;

    /** 实际释放时间：手工释放时间或到期时间 */
    private LocalDateTime releaseTime;

    /** 释放结论：手工释放必填，到期由系统补写 */
    private String releaseConclusion;

    private String releaseOperator;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 预留板清单（联表查询，非本表字段） */
    @TableField(exist = false)
    private List<PadMoldReserveItem> items;

    /** 预留板数量（联表统计，非本表字段） */
    @TableField(exist = false)
    private Integer padCount;
}
