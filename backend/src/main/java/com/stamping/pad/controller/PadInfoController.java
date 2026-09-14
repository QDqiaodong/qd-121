package com.stamping.pad.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.common.Result;
import com.stamping.pad.dto.BindLayerDTO;
import com.stamping.pad.dto.PadInfoDTO;
import com.stamping.pad.dto.PadQueryDTO;
import com.stamping.pad.dto.UnbindLayerDTO;
import com.stamping.pad.entity.LayerAdjustRecord;
import com.stamping.pad.entity.PadInfo;
import com.stamping.pad.service.ExcelExportService;
import com.stamping.pad.service.LayerAdjustRecordService;
import com.stamping.pad.service.PadArrivalService;
import com.stamping.pad.service.PadBorrowService;
import com.stamping.pad.service.PadInfoService;
import com.stamping.pad.service.PadMaintenanceService;
import com.stamping.pad.service.PadMoldReserveService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pad")
@RequiredArgsConstructor
public class PadInfoController {

    private final PadInfoService padInfoService;
    private final ExcelExportService excelExportService;
    private final LayerAdjustRecordService recordService;
    private final PadBorrowService padBorrowService;
    private final PadMaintenanceService padMaintenanceService;
    private final PadMoldReserveService padMoldReserveService;
    private final PadArrivalService padArrivalService;

    @GetMapping("/page")
    public Result<Page<PadInfo>> page(PadQueryDTO query) {
        return Result.success(padInfoService.pageList(query));
    }

    @GetMapping("/{id}")
    public Result<PadInfo> getById(@PathVariable Long id) {
        return Result.success(padInfoService.getById(id));
    }

    @GetMapping("/layer/{layerCode}")
    public Result<List<PadInfo>> listByLayerCode(@PathVariable String layerCode) {
        return Result.success(padInfoService.listByLayerCode(layerCode));
    }

    @GetMapping("/shelf/{shelfCode}")
    public Result<List<PadInfo>> listByShelfCode(@PathVariable String shelfCode) {
        return Result.success(padInfoService.listByShelfCode(shelfCode));
    }

    @PostMapping
    public Result<PadInfo> create(@Valid @RequestBody PadInfoDTO dto) {
        return Result.success(padInfoService.create(dto));
    }

    @PutMapping
    public Result<PadInfo> update(@Valid @RequestBody PadInfoDTO dto) {
        return Result.success(padInfoService.update(dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        padInfoService.delete(id);
        return Result.success();
    }

    @PostMapping("/bind")
    public Result<Void> bindLayer(@Valid @RequestBody BindLayerDTO dto) {
        padInfoService.bindLayer(dto);
        return Result.success();
    }

    @PostMapping("/unbind")
    public Result<Void> unbindLayer(@Valid @RequestBody UnbindLayerDTO dto) {
        padInfoService.unbindLayer(dto);
        return Result.success();
    }

    @GetMapping("/{id}/records")
    public Result<List<LayerAdjustRecord>> listRecords(@PathVariable Long id) {
        return Result.success(recordService.listByPadId(id));
    }

    @GetMapping("/export/layer/{layerCode}")
    public void exportByLayer(@PathVariable String layerCode, HttpServletResponse response) throws IOException {
        excelExportService.exportPadsByLayer(layerCode, response);
    }

    @GetMapping("/export/all")
    public void exportAll(HttpServletResponse response) throws IOException {
        excelExportService.exportAllPads(response);
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCount", padInfoService.countTotal());
        stats.put("unboundCount", padInfoService.countUnbound());
        stats.put("boundCount", padInfoService.countTotal() - padInfoService.countUnbound());
        stats.putAll(padBorrowService.statistics());
        stats.putAll(padMaintenanceService.statistics());
        stats.put("reserveActiveCount", padMoldReserveService.statistics().get("activeCount"));
        // 到货待检单独标出：待检层垫板块数（不计可用库存）与待检批次数
        Map<String, Object> arrivalStats = padArrivalService.statistics();
        stats.put("quarantineCount", arrivalStats.get("pendingPadCount"));
        stats.put("arrivalPendingCount", arrivalStats.get("pendingCount"));
        return Result.success(stats);
    }
}
