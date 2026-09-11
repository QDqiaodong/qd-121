package com.stamping.pad.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pad_inventory_item")
public class PadInventoryItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sheetId;

    /** LEDGER-档案在架清单生成、EXTRA-现场多出补录 */
    private String itemType;

    private Long padId;

    private String padCode;

    private String moldType;

    /** 该明细所属层位：档案明细取开单时在架层，多出明细为用户指定层 */
    private String layerCode;

    /** 点检结果：MATCH-账实相符、MISSING-缺失、EXTRA-多出（多出明细固定） */
    private String checkResult;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ---------------- 联表/实时字段（非本表字段） ----------------

    /** 层位名称（联表 shelf_layer） */
    @TableField(exist = false)
    private String layerName;

    /** 垫板实时在架层（联 pad_info，用于点检中提示已移层） */
    @TableField(exist = false)
    private String currentLayerCode;

    /** 垫板实时保养状态 */
    @TableField(exist = false)
    private String maintenanceStatus;

    /** 垫板是否处于领用离架中 */
    @TableField(exist = false)
    private String borrowStatus;
}
