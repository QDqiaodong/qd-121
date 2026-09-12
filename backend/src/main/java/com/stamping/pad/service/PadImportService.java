package com.stamping.pad.service;

import com.alibaba.excel.EasyExcel;
import com.stamping.pad.entity.LayerBlockRecord;
import com.stamping.pad.entity.PadInfo;
import com.stamping.pad.entity.ShelfLayer;
import com.stamping.pad.mapper.LayerBlockRecordMapper;
import com.stamping.pad.mapper.PadInfoMapper;
import com.stamping.pad.mapper.ShelfLayerMapper;
import com.stamping.pad.vo.PadImportResultVO;
import com.stamping.pad.vo.PadImportRowVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 垫板档案 Excel 批量导入：
 * 1. 上传解析后逐行校验必填项、文件内/库内重复编号、无效初始层位与尺寸格式；
 * 2. 预览阶段仅返回带 valid/errorMessage 标记的行数据，不落库；
 * 3. 确认导入时再次逐行校验，仅导入合格数据，返回成功与失败明细。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PadImportService {

    /** 单次导入数据行数上限（不含表头） */
    private static final int MAX_ROWS = 2000;
    private static final int PAD_CODE_MAX_LENGTH = 64;
    private static final int MOLD_TYPE_MAX_LENGTH = 128;
    private static final int REMARK_MAX_LENGTH = 512;
    private static final BigDecimal DIMENSION_MAX = new BigDecimal("99999999.99");

    private final PadInfoMapper padInfoMapper;
    private final ShelfLayerMapper shelfLayerMapper;
    private final LayerBlockRecordMapper layerBlockRecordMapper;
    private final PadInfoService padInfoService;

    /**
     * 解析 Excel 并逐行校验，返回带错误标记的预览数据。
     */
    public List<PadImportRowVO> preview(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("上传文件为空，请选择有效的 Excel 文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null
                || (!filename.toLowerCase().endsWith(".xlsx") && !filename.toLowerCase().endsWith(".xls"))) {
            throw new RuntimeException("仅支持 .xlsx / .xls 格式的 Excel 文件");
        }

        List<PadImportRowVO> rows;
        try {
            rows = EasyExcel.read(file.getInputStream())
                    .head(PadImportRowVO.class)
                    .sheet()
                    .doReadSync();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Excel 解析失败：", e);
            throw new RuntimeException("Excel 解析失败，请检查文件格式与表头是否与模板一致");
        }

        if (rows == null || rows.isEmpty()) {
            throw new RuntimeException("未读取到任何数据行，请按模板填写后再上传");
        }
        if (rows.size() > MAX_ROWS) {
            throw new RuntimeException("单次最多导入 " + MAX_ROWS + " 行数据，请拆分后再导入");
        }

        Set<String> dbPadCodes = loadDbPadCodes();
        Set<String> validLayerCodes = loadValidLayerCodes();
        Set<String> blockedLayerCodes = loadBlockedLayerCodes();

        List<PadImportRowVO> result = new ArrayList<>();
        Set<String> filePadCodes = new HashSet<>();
        int rowNum = 1;
        for (PadImportRowVO row : rows) {
            rowNum++;
            if (isBlankRow(row)) {
                continue;
            }
            row.setRowNum(rowNum);
            List<String> errors = validateRow(row, filePadCodes, dbPadCodes, validLayerCodes, blockedLayerCodes);
            String padCode = trim(row.getPadCode());
            if (!padCode.isEmpty()) {
                filePadCodes.add(padCode);
            }
            row.setValid(errors.isEmpty());
            row.setErrorMessage(String.join("；", errors));
            result.add(row);
        }

        if (result.isEmpty()) {
            throw new RuntimeException("未读取到任何有效数据行（空行已忽略）");
        }
        return result;
    }

    /**
     * 确认导入：再次逐行校验，仅将合格行落库；单行异常计入失败明细，不影响其他行。
     */
    public PadImportResultVO confirmImport(List<PadImportRowVO> rows) {
        PadImportResultVO result = new PadImportResultVO();
        List<PadImportRowVO> successRows = new ArrayList<>();
        List<PadImportRowVO> failRows = new ArrayList<>();

        Set<String> dbPadCodes = loadDbPadCodes();
        Set<String> validLayerCodes = loadValidLayerCodes();
        Set<String> blockedLayerCodes = loadBlockedLayerCodes();
        Set<String> batchPadCodes = new HashSet<>();

        int index = 1;
        for (PadImportRowVO row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getRowNum() == null) {
                row.setRowNum(index + 1);
            }
            index++;

            List<String> errors = validateRow(row, batchPadCodes, dbPadCodes, validLayerCodes, blockedLayerCodes);
            String padCode = trim(row.getPadCode());
            if (!padCode.isEmpty()) {
                batchPadCodes.add(padCode);
            }
            if (!errors.isEmpty()) {
                row.setValid(false);
                row.setErrorMessage(String.join("；", errors));
                failRows.add(row);
                continue;
            }

            try {
                padInfoService.importRow(buildPadInfo(row));
                row.setValid(true);
                row.setErrorMessage(null);
                dbPadCodes.add(padCode);
                successRows.add(row);
            } catch (RuntimeException e) {
                log.warn("垫板导入失败，行号{}，编号{}：{}", row.getRowNum(), padCode, e.getMessage());
                row.setValid(false);
                row.setErrorMessage(e.getMessage());
                failRows.add(row);
            }
        }

        result.setSuccessCount(successRows.size());
        result.setFailCount(failRows.size());
        result.setSuccessRows(successRows);
        result.setFailRows(failRows);
        return result;
    }

    /**
     * 下载导入模板（含表头与一行示例数据）。
     */
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("垫板档案批量导入模板", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        PadImportRowVO sample = new PadImportRowVO();
        sample.setPadCode("DB-20260901-01");
        sample.setMoldType("示例：160T冲床模具A");
        sample.setLength("800");
        sample.setWidth("500");
        sample.setThickness("20");
        sample.setShelfLayerCode("A-01-01");
        sample.setRemark("示例行，正式导入前请删除");

        EasyExcel.write(response.getOutputStream(), PadImportRowVO.class)
                .sheet("垫板档案")
                .doWrite(List.of(sample));
    }

    /**
     * 逐行校验，返回错误信息集合（空集合表示合格）。
     */
    private List<String> validateRow(PadImportRowVO row,
                                     Set<String> filePadCodes,
                                     Set<String> dbPadCodes,
                                     Set<String> validLayerCodes,
                                     Set<String> blockedLayerCodes) {
        List<String> errors = new ArrayList<>();

        String padCode = trim(row.getPadCode());
        row.setPadCode(padCode);
        if (padCode.isEmpty()) {
            errors.add("垫板编号为必填项");
        } else {
            if (padCode.length() > PAD_CODE_MAX_LENGTH) {
                errors.add("垫板编号长度不能超过 " + PAD_CODE_MAX_LENGTH + " 个字符");
            }
            if (filePadCodes.contains(padCode)) {
                errors.add("垫板编号在文件内重复");
            }
            if (dbPadCodes.contains(padCode)) {
                errors.add("垫板编号在系统中已存在");
            }
        }

        String moldType = trim(row.getMoldType());
        row.setMoldType(moldType);
        if (moldType.length() > MOLD_TYPE_MAX_LENGTH) {
            errors.add("适配模具长度不能超过 " + MOLD_TYPE_MAX_LENGTH + " 个字符");
        }

        BigDecimal length = validateDimension("长度", trim(row.getLength()), errors);
        BigDecimal width = validateDimension("宽度", trim(row.getWidth()), errors);
        BigDecimal thickness = validateDimension("厚度", trim(row.getThickness()), errors);

        String layerCode = trim(row.getShelfLayerCode());
        row.setShelfLayerCode(layerCode);
        if (!layerCode.isEmpty() && !validLayerCodes.contains(layerCode)) {
            errors.add("初始层位【" + layerCode + "】不存在，请在货架层位管理中确认");
        } else if (!layerCode.isEmpty() && blockedLayerCodes.contains(layerCode)) {
            // 封锁中的层位禁止导入占位，解除封锁后可重新导入
            errors.add("初始层位【" + layerCode + "】处于封锁中（破损/清扫/检修），不可导入占位");
        }

        String remark = trim(row.getRemark());
        row.setRemark(remark);
        if (remark.length() > REMARK_MAX_LENGTH) {
            errors.add("备注长度不能超过 " + REMARK_MAX_LENGTH + " 个字符");
        }

        // 将成功解析的数值规范化回填，便于预览展示与确认导入复用
        row.setLength(length != null ? length.stripTrailingZeros().toPlainString() : "");
        row.setWidth(width != null ? width.stripTrailingZeros().toPlainString() : "");
        row.setThickness(thickness != null ? thickness.stripTrailingZeros().toPlainString() : "");
        return errors;
    }

    /**
     * 校验单个尺寸字段：允许为空；非空时必须为正数，小数位不超过 2 位且不超字段上限。
     */
    private BigDecimal validateDimension(String fieldName, String value, List<String> errors) {
        if (value.isEmpty()) {
            return null;
        }
        BigDecimal decimal;
        try {
            decimal = new BigDecimal(value);
        } catch (NumberFormatException e) {
            errors.add(fieldName + "格式无效（应为数字）");
            return null;
        }
        if (decimal.signum() <= 0) {
            errors.add(fieldName + "必须大于 0");
            return null;
        }
        if (decimal.stripTrailingZeros().scale() > 2) {
            errors.add(fieldName + "小数位不能超过 2 位");
            return null;
        }
        if (decimal.compareTo(DIMENSION_MAX) > 0) {
            errors.add(fieldName + "超出允许范围（最大 " + DIMENSION_MAX.toPlainString() + "）");
            return null;
        }
        return decimal;
    }

    private boolean isBlankRow(PadImportRowVO row) {
        return trim(row.getPadCode()).isEmpty()
                && trim(row.getMoldType()).isEmpty()
                && trim(row.getLength()).isEmpty()
                && trim(row.getWidth()).isEmpty()
                && trim(row.getThickness()).isEmpty()
                && trim(row.getShelfLayerCode()).isEmpty()
                && trim(row.getRemark()).isEmpty();
    }

    private PadInfo buildPadInfo(PadImportRowVO row) {
        PadInfo padInfo = new PadInfo();
        padInfo.setPadCode(trim(row.getPadCode()));
        padInfo.setMoldType(emptyToNull(trim(row.getMoldType())));
        padInfo.setLength(parseDecimalOrNull(row.getLength()));
        padInfo.setWidth(parseDecimalOrNull(row.getWidth()));
        padInfo.setThickness(parseDecimalOrNull(row.getThickness()));
        String layerCode = trim(row.getShelfLayerCode());
        padInfo.setShelfLayerCode(layerCode.isEmpty() ? null : layerCode);
        padInfo.setRemark(emptyToNull(trim(row.getRemark())));
        return padInfo;
    }

    private BigDecimal parseDecimalOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return new BigDecimal(value.trim());
    }

    private Set<String> loadDbPadCodes() {
        Set<String> codes = new HashSet<>();
        padInfoMapper.selectList(null).forEach(pad -> codes.add(pad.getPadCode()));
        return codes;
    }

    private Set<String> loadValidLayerCodes() {
        Set<String> codes = new HashSet<>();
        List<ShelfLayer> layers = shelfLayerMapper.selectList(null);
        if (layers != null) {
            layers.forEach(layer -> codes.add(layer.getLayerCode()));
        }
        return codes;
    }

    /** 封锁中层位编码集合：导入校验据此拦截“导入占位”到封锁层 */
    private Set<String> loadBlockedLayerCodes() {
        Set<String> codes = new HashSet<>();
        List<LayerBlockRecord> activeBlocks = layerBlockRecordMapper.selectActiveBlocks();
        if (activeBlocks != null) {
            activeBlocks.forEach(block -> codes.add(block.getLayerCode()));
        }
        return codes;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
