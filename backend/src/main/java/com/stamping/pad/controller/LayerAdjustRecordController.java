package com.stamping.pad.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.common.Result;
import com.stamping.pad.dto.RecordQueryDTO;
import com.stamping.pad.entity.LayerAdjustRecord;
import com.stamping.pad.service.ExcelExportService;
import com.stamping.pad.service.LayerAdjustRecordService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/adjust-record")
@RequiredArgsConstructor
public class LayerAdjustRecordController {

    private final LayerAdjustRecordService recordService;
    private final ExcelExportService excelExportService;

    @GetMapping("/page")
    public Result<Page<LayerAdjustRecord>> page(RecordQueryDTO query) {
        return Result.success(recordService.pageList(query));
    }

    @GetMapping("/{id}")
    public Result<LayerAdjustRecord> getById(@PathVariable Long id) {
        return Result.success(recordService.getById(id));
    }

    @GetMapping("/pad/{padId}")
    public Result<?> listByPadId(@PathVariable Long padId) {
        return Result.success(recordService.listByPadId(padId));
    }

    @GetMapping("/export")
    public void exportRecords(RecordQueryDTO query, HttpServletResponse response) throws IOException {
        query.setPageSize(10000L);
        Page<LayerAdjustRecord> page = recordService.pageList(query);
        excelExportService.exportAdjustRecords(page.getRecords(), response);
    }
}
