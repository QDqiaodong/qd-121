package com.stamping.pad.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.common.Result;
import com.stamping.pad.dto.LayerCapacityExpandDTO;
import com.stamping.pad.dto.LayerCapacityExpandFinishDTO;
import com.stamping.pad.dto.LayerCapacityExpandQueryDTO;
import com.stamping.pad.entity.LayerCapacityExpandRecord;
import com.stamping.pad.service.LayerCapacityExpandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/layer-capacity-expand")
@RequiredArgsConstructor
public class LayerCapacityExpandController {

    private final LayerCapacityExpandService expandService;

    @GetMapping("/page")
    public Result<Page<LayerCapacityExpandRecord>> page(LayerCapacityExpandQueryDTO query) {
        return Result.success(expandService.pageList(query));
    }

    @GetMapping("/active")
    public Result<List<LayerCapacityExpandRecord>> listEffective() {
        return Result.success(expandService.listEffectiveExpansions());
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.success(expandService.statistics());
    }

    @PostMapping
    public Result<LayerCapacityExpandRecord> register(@RequestBody LayerCapacityExpandDTO dto) {
        return Result.success(expandService.register(dto));
    }

    @PostMapping("/finish")
    public Result<LayerCapacityExpandRecord> finish(@RequestBody LayerCapacityExpandFinishDTO dto) {
        return Result.success(expandService.finish(dto));
    }
}
