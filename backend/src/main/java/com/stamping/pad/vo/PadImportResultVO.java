package com.stamping.pad.vo;

import lombok.Data;

import java.util.List;

/**
 * 批量导入结果明细。
 */
@Data
public class PadImportResultVO {

    /** 成功导入条数 */
    private Integer successCount;

    /** 导入失败条数 */
    private Integer failCount;

    /** 成功明细 */
    private List<PadImportRowVO> successRows;

    /** 失败明细（含失败原因） */
    private List<PadImportRowVO> failRows;
}
