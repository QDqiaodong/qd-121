package com.stamping.pad.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 质检通过：整批转正式层，逐块指定目标层位；质检结论必填。
 */
@Data
public class PadArrivalPassDTO {

    /** 批次ID */
    private Long id;

    /** 质检时间（缺省取当前时间） */
    private LocalDateTime inspectTime;

    /** 质检人（缺省取登记经办人） */
    private String inspector;

    /** 质检结论（必填） */
    private String inspectConclusion;

    /** 逐块转正式层位：批内每块垫板都必须指定目标层位 */
    private List<Target> targets;

    @Data
    public static class Target {
        private Long padId;
        private String layerCode;
    }
}
