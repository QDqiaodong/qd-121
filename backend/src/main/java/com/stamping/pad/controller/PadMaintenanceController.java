package com.stamping.pad.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.common.Result;
import com.stamping.pad.dto.MaintenanceRecordQueryDTO;
import com.stamping.pad.dto.PadMaintenanceDTO;
import com.stamping.pad.entity.PadMaintenanceRecord;
import com.stamping.pad.service.PadMaintenanceService;
import com.stamping.pad.vo.PadMaintenanceDetailVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/maintenance")
@RequiredArgsConstructor
public class PadMaintenanceController {

    private final PadMaintenanceService padMaintenanceService;

    @GetMapping("/page")
    public Result<Page<PadMaintenanceRecord>> page(MaintenanceRecordQueryDTO query) {
        return Result.success(padMaintenanceService.pageList(query));
    }

    @GetMapping("/{id}")
    public Result<PadMaintenanceRecord> getById(@PathVariable Long id) {
        return Result.success(padMaintenanceService.getById(id));
    }

    @GetMapping("/pad/{padId}")
    public Result<List<PadMaintenanceRecord>> listByPadId(@PathVariable Long padId) {
        return Result.success(padMaintenanceService.listByPadId(padId));
    }

    @GetMapping("/pad/{padId}/detail")
    public Result<PadMaintenanceDetailVO> detail(@PathVariable Long padId) {
        return Result.success(padMaintenanceService.detail(padId));
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.success(padMaintenanceService.statistics());
    }

    @PostMapping
    public Result<PadMaintenanceRecord> register(@Valid @RequestBody PadMaintenanceDTO dto) {
        return Result.success(padMaintenanceService.register(dto));
    }
}
