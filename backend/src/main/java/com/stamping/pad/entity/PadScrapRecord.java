package com.stamping.pad.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("pad_scrap_record")
public class PadScrapRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long padId;

    private String padCode;

    /** 批准人 */
    private String approver;

    /** 报废去向：如废品仓、回收商、就地销毁 */
    private String destination;

    private LocalDateTime scrapTime;

    /** 报废照片路径，多张以英文逗号分隔 */
    private String photoPaths;

    /** 报废时所在层位（出库前原层位） */
    private String originLayerCode;

    /** 关联的报废建议保养记录ID */
    private Long maintenanceRecordId;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 报废照片列表（photo_paths 拆分，非本表字段） */
    @TableField(exist = false)
    private List<String> photos;

    /** 适配模具（联表查询，非本表字段） */
    @TableField(exist = false)
    private String moldType;
}
