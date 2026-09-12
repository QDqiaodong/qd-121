package com.stamping.pad.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("pad_info")
public class PadInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String padCode;

    private String moldType;

    private BigDecimal length;

    private BigDecimal width;

    private BigDecimal thickness;

    private String imagePath;

    private String shelfLayerCode;

    private LocalDateTime bindTime;

    /** 保养状态：AVAILABLE-可用、PENDING-待检、DISABLED-停用 */
    private String maintenanceStatus;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String remark;

    @TableField(exist = false)
    private String layerName;

    @TableField(exist = false)
    private String shelfCode;

    @TableField(exist = false)
    private String borrowStatus;

    @TableField(exist = false)
    private String borrower;

    @TableField(exist = false)
    private String productionLine;

    @TableField(exist = false)
    private LocalDateTime checkoutTime;

    /** 是否存在保养“报废建议”（联表/子查询，非本表字段） */
    @TableField(exist = false)
    private Boolean scrapSuggested;
}
