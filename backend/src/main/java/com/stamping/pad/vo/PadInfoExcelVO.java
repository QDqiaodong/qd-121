package com.stamping.pad.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentRowHeight;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;

import java.math.BigDecimal;

@Data
@HeadRowHeight(20)
@ContentRowHeight(18)
@ColumnWidth(20)
public class PadInfoExcelVO {

    @ExcelProperty("垫板编号")
    @ColumnWidth(20)
    private String padCode;

    @ExcelProperty("适配模具")
    @ColumnWidth(25)
    private String moldType;

    @ExcelProperty("长度(mm)")
    @ColumnWidth(15)
    private BigDecimal length;

    @ExcelProperty("宽度(mm)")
    @ColumnWidth(15)
    private BigDecimal width;

    @ExcelProperty("厚度(mm)")
    @ColumnWidth(15)
    private BigDecimal thickness;

    @ExcelProperty("货架分层编码")
    @ColumnWidth(20)
    private String shelfLayerCode;

    @ExcelProperty("分层名称")
    @ColumnWidth(25)
    private String layerName;

    @ExcelProperty("所属货架")
    @ColumnWidth(15)
    private String shelfCode;

    @ExcelProperty("绑定时间")
    @ColumnWidth(25)
    private String bindTimeStr;

    @ExcelProperty("备注")
    @ColumnWidth(30)
    private String remark;
}
