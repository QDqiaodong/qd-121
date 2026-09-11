package com.stamping.pad.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("shelf_layer")
public class ShelfLayer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String layerCode;

    private String shelfCode;

    private String layerName;

    private Integer layerOrder;

    /** 层位容量配额：该层最多可存放的在架垫板数 */
    private Integer capacity;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String remark;

    @TableField(exist = false)
    private Integer padCount;

    @TableField(exist = false)
    private List<PadInfo> padList;
}
