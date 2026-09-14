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

    /** 库存状态：QUARANTINE-到货待检、OFFICIAL-正式在库、REJECTED-判退离库 */
    private String stockStatus;
}
