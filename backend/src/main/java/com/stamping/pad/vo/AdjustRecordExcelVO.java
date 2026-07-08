package com.stamping.pad.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentRowHeight;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;

@Data
@HeadRowHeight(20)
@ContentRowHeight(18)
@ColumnWidth(20)
public class AdjustRecordExcelVO {

    @ExcelProperty("垫板编号")
    @ColumnWidth(20)
    private String padCode;

    @ExcelProperty("原分层编码")
    @ColumnWidth(20)
    private String oldLayerCode;

    @ExcelProperty("新分层编码")
    @ColumnWidth(20)
    private String newLayerCode;

    @ExcelProperty("调整类型")
    @ColumnWidth(15)
    private String adjustTypeStr;

    @ExcelProperty("操作人")
    @ColumnWidth(15)
    private String operator;

    @ExcelProperty("调整原因")
    @ColumnWidth(30)
    private String adjustReason;

    @ExcelProperty("调整时间")
    @ColumnWidth(25)
    private String adjustTimeStr;
}
