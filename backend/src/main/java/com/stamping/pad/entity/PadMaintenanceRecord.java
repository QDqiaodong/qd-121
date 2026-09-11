package com.stamping.pad.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pad_maintenance_record")
public class PadMaintenanceRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long padId;

    private String padCode;

    private String maintenanceType;

    private String handler;

    private LocalDateTime maintenanceTime;

    /** NORMAL-正常、REPAIRED-已修复、ABNORMAL-异常待处理、SCRAPPED-报废建议 */
    private String maintenanceResult;

    /** AVAILABLE/PENDING/DISABLED，首次保养可能为空 */
    private String statusBefore;

    /** 保养后状态：AVAILABLE-可用、PENDING-待检、DISABLED-停用 */
    private String statusAfter;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 垫板当前保养状态（联表查询，非本表字段） */
    @TableField(exist = false)
    private String currentStatus;

    @TableField(exist = false)
    private String moldType;
}
