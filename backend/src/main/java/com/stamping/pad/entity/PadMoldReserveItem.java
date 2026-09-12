package com.stamping.pad.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 换模垫板预留明细：预留单内的垫板清单。
 * pad_code/layer_code 登记时做快照；垫板当前层位与保养状态联表 pad_info 实时带出，
 * 用于刷新后回看预留板是否仍在架、是否已被释放。
 */
@Data
@TableName("pad_mold_reserve_item")
public class PadMoldReserveItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reserveId;

    private Long padId;

    /** 垫板编码（登记时快照） */
    private String padCode;

    /** 适配模具（登记时快照） */
    private String moldType;

    /** 预留时所在层位（登记时快照） */
    private String layerCode;

    private LocalDateTime createTime;

    /** 分层名称（联表查询，非本表字段） */
    @TableField(exist = false)
    private String layerName;

    /** 垫板当前所在层位（联表 pad_info 实时查询，非本表字段） */
    @TableField(exist = false)
    private String currentLayerCode;

    /** 垫板当前保养状态（联表 pad_info 实时查询，非本表字段） */
    @TableField(exist = false)
    private String maintenanceStatus;
}
