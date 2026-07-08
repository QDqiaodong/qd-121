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
}
