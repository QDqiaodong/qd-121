package com.stamping.pad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PadScrapDTO {

    @NotNull(message = "垫板ID不能为空")
    private Long padId;

    /** 关联的报废建议保养记录ID（可选） */
    private Long maintenanceRecordId;

    @NotBlank(message = "批准人不能为空")
    private String approver;

    @NotBlank(message = "报废去向不能为空")
    private String destination;

    private LocalDateTime scrapTime;

    /** 报废照片路径，至少一张 */
    private List<String> photoPaths;

    private String remark;
}
