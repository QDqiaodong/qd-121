package com.stamping.pad.service;

import com.alibaba.excel.EasyExcel;
import com.stamping.pad.entity.LayerAdjustRecord;
import com.stamping.pad.entity.PadInfo;
import com.stamping.pad.vo.AdjustRecordExcelVO;
import com.stamping.pad.vo.PadInfoExcelVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExcelExportService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PadInfoService padInfoService;
    private final LayerAdjustRecordService recordService;

    public void exportPadsByLayer(String layerCode, HttpServletResponse response) throws IOException {
        List<PadInfo> padList = padInfoService.listByLayerCode(layerCode);
        List<PadInfoExcelVO> voList = convertPadInfoToVO(padList);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("层位_" + layerCode + "_垫板清单", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        EasyExcel.write(response.getOutputStream(), PadInfoExcelVO.class)
                .sheet("垫板清单")
                .doWrite(voList);
    }

    public void exportAllPads(HttpServletResponse response) throws IOException {
        List<PadInfo> padList = padInfoService.pageList(createQueryAll()).getRecords();
        List<PadInfoExcelVO> voList = convertPadInfoToVO(padList);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("全部垫板档案清单", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        EasyExcel.write(response.getOutputStream(), PadInfoExcelVO.class)
                .sheet("垫板档案清单")
                .doWrite(voList);
    }

    public void exportAdjustRecords(List<LayerAdjustRecord> records, HttpServletResponse response) throws IOException {
        List<AdjustRecordExcelVO> voList = convertRecordToVO(records);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("层位调整记录台账", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        EasyExcel.write(response.getOutputStream(), AdjustRecordExcelVO.class)
                .sheet("调整记录")
                .doWrite(voList);
    }

    private List<PadInfoExcelVO> convertPadInfoToVO(List<PadInfo> list) {
        List<PadInfoExcelVO> voList = new ArrayList<>();
        for (PadInfo pad : list) {
            PadInfoExcelVO vo = new PadInfoExcelVO();
            vo.setPadCode(pad.getPadCode());
            vo.setMoldType(pad.getMoldType());
            vo.setLength(pad.getLength());
            vo.setWidth(pad.getWidth());
            vo.setThickness(pad.getThickness());
            vo.setShelfLayerCode(pad.getShelfLayerCode() != null ? pad.getShelfLayerCode() : "未绑定");
            vo.setLayerName(pad.getLayerName() != null ? pad.getLayerName() : "-");
            vo.setShelfCode(pad.getShelfCode() != null ? pad.getShelfCode() : "-");
            vo.setBindTimeStr(pad.getBindTime() != null ? pad.getBindTime().format(FORMATTER) : "-");
            vo.setRemark(pad.getRemark());
            voList.add(vo);
        }
        return voList;
    }

    private List<AdjustRecordExcelVO> convertRecordToVO(List<LayerAdjustRecord> list) {
        List<AdjustRecordExcelVO> voList = new ArrayList<>();
        for (LayerAdjustRecord record : list) {
            AdjustRecordExcelVO vo = new AdjustRecordExcelVO();
            vo.setPadCode(record.getPadCode());
            vo.setOldLayerCode(record.getOldLayerCode() != null ? record.getOldLayerCode() : "无");
            vo.setNewLayerCode(record.getNewLayerCode() != null ? record.getNewLayerCode() : "已解绑");
            vo.setAdjustTypeStr(convertAdjustType(record.getAdjustType()));
            vo.setOperator(record.getOperator());
            vo.setAdjustReason(record.getAdjustReason());
            vo.setAdjustTimeStr(record.getAdjustTime() != null ? record.getAdjustTime().format(FORMATTER) : "");
            voList.add(vo);
        }
        return voList;
    }

    private String convertAdjustType(String type) {
        return switch (type) {
            case "BIND" -> "初始绑定";
            case "REBIND" -> "变更绑定";
            case "UNBIND" -> "解除绑定";
            default -> type;
        };
    }

    private com.stamping.pad.dto.PadQueryDTO createQueryAll() {
        com.stamping.pad.dto.PadQueryDTO dto = new com.stamping.pad.dto.PadQueryDTO();
        dto.setPageNum(1L);
        dto.setPageSize(10000L);
        return dto;
    }
}
