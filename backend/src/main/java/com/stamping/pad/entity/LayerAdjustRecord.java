package com.stamping.pad.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("layer_adjust_record")
public class LayerAdjustRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long padId;

    private String padCode;

    private String oldLayerCode;

    private String newLayerCode;

    private String adjustType;

    private String operator;

    private String adjustReason;

    private LocalDateTime adjustTime;
}
