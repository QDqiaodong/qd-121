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

    /** 当前生效的换模预留单ID（生效时段内，非本表字段）：非空表示垫板预留中，禁止领用/解绑/换层 */
    @TableField(exist = false)
    private Long reserveId;

    /** 当前生效预留对应的模具编码（非本表字段），用于领用可选板、档案状态的预留标注 */
    @TableField(exist = false)
    private String reserveMoldCode;
}
