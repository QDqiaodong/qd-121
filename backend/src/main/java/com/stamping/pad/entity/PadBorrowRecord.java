package com.stamping.pad.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pad_borrow_record")
public class PadBorrowRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long padId;

    private String padCode;

    private String borrower;

    private String productionLine;

    private String purpose;

    private LocalDateTime checkoutTime;

    private LocalDateTime expectedReturnTime;

    private LocalDateTime returnTime;

    private String originLayerCode;

    private String returnLayerCode;

    /** BORROWED-领用中 RETURNED-已归还 */
    private String status;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableField(exist = false)
    private Boolean overdue;

    @TableField(exist = false)
    private String returnLayerName;
}
