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
public class BorrowRecordExcelVO {

    @ExcelProperty("垫板编号")
    @ColumnWidth(20)
    private String padCode;

    @ExcelProperty("状态")
    @ColumnWidth(12)
    private String statusStr;

    @ExcelProperty("领用人")
    @ColumnWidth(15)
    private String borrower;

    @ExcelProperty("产线/工位")
    @ColumnWidth(20)
    private String productionLine;

    @ExcelProperty("用途")
    @ColumnWidth(30)
    private String purpose;

    @ExcelProperty("原层位")
    @ColumnWidth(18)
    private String originLayerCode;

    @ExcelProperty("归还层位")
    @ColumnWidth(18)
    private String returnLayerCode;

    @ExcelProperty("领用时间")
    @ColumnWidth(22)
    private String checkoutTimeStr;

    @ExcelProperty("预计归还时间")
    @ColumnWidth(22)
    private String expectedReturnTimeStr;

    @ExcelProperty("实际归还时间")
    @ColumnWidth(22)
    private String returnTimeStr;
}
