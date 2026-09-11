package com.stamping.pad.dto;

import lombok.Data;

@Data
public class PadQueryDTO {

    private Long pageNum = 1L;

    private Long pageSize = 10L;

    private String padCode;

    private String moldType;

    private String shelfLayerCode;

    private String shelfCode;

    /** 保养状态：AVAILABLE-可用、PENDING-待检、DISABLED-停用 */
    private String maintenanceStatus;
}
