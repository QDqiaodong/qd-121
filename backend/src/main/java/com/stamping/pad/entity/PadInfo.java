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

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String remark;

    @TableField(exist = false)
    private String layerName;

    @TableField(exist = false)
    private String shelfCode;
}
