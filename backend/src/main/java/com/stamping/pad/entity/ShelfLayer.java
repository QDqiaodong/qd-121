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

    /** 当前生效的封锁记录（非本表字段）：非空表示层位封锁中，禁止绑定/换绑/归还上架/导入占位 */
    @TableField(exist = false)
    private LayerBlockRecord activeBlock;

    /** 当前生效的临时扩容记录（非本表字段）：非空表示层位扩容中，按扩容后配额校验 */
    @TableField(exist = false)
    private LayerCapacityExpandRecord activeExpand;

    /** 当前实际配额（非本表字段）：扩容期内取扩容后配额，否则取 capacity；占用进度与可选范围统一按此展示 */
    @TableField(exist = false)
    private Integer effectiveCapacity;

    /** 覆盖本层的待闭环盘点单（非本表字段）：非空表示盘点差异未平账，未闭环前禁止归还上架 */
    @TableField(exist = false)
    private PadInventorySheet activeUnbalanced;
}
