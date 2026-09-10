package com.stamping.pad.vo;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 垫板档案批量导入行数据。
 * 全部使用字符串接收，避免单个单元格类型转换失败导致整文件无法解析，
 * 具体格式由业务逐行校验并标记错误。
 */
@Data
public class PadImportRowVO {

    @ExcelProperty("垫板编号")
    private String padCode;

    @ExcelProperty("适配模具")
    private String moldType;

    @ExcelProperty("长度(mm)")
    private String length;

    @ExcelProperty("宽度(mm)")
    private String width;

    @ExcelProperty("厚度(mm)")
    private String thickness;

    @ExcelProperty("初始层位")
    private String shelfLayerCode;

    @ExcelProperty("备注")
    private String remark;

    /** Excel 行号（表头为第 1 行，数据从第 2 行开始） */
    @ExcelIgnore
    private Integer rowNum;

    /** 校验是否通过 */
    @ExcelIgnore
    private Boolean valid;

    /** 错误信息，多个错误用“；”拼接 */
    @ExcelIgnore
    private String errorMessage;
}
