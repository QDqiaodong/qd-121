package com.stamping.pad.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.common.Result;
import com.stamping.pad.dto.PadArrivalDTO;
import com.stamping.pad.dto.PadArrivalPassDTO;
import com.stamping.pad.dto.PadArrivalQueryDTO;
import com.stamping.pad.dto.PadArrivalRejectDTO;
import com.stamping.pad.entity.PadArrivalBatch;
import com.stamping.pad.service.PadArrivalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/arrival")
@RequiredArgsConstructor
public class PadArrivalController {

    private final PadArrivalService padArrivalService;

    @GetMapping("/page")
    public Result<Page<PadArrivalBatch>> page(PadArrivalQueryDTO query) {
        return Result.success(padArrivalService.pageList(query));
    }

    @GetMapping("/{id}")
    public Result<PadArrivalBatch> getById(@PathVariable Long id) {
        return Result.success(padArrivalService.getById(id));
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.success(padArrivalService.statistics());
    }

    @PostMapping
    public Result<PadArrivalBatch> register(@RequestBody PadArrivalDTO dto) {
        return Result.success(padArrivalService.register(dto));
    }

    @PostMapping("/pass")
    public Result<PadArrivalBatch> pass(@RequestBody PadArrivalPassDTO dto) {
        return Result.success(padArrivalService.passInspection(dto));
    }

    @PostMapping("/reject")
    public Result<PadArrivalBatch> reject(@RequestBody PadArrivalRejectDTO dto) {
        return Result.success(padArrivalService.reject(dto));
    }
}
