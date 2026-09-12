package com.stamping.pad.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.common.Result;
import com.stamping.pad.dto.LayerBlockDTO;
import com.stamping.pad.dto.LayerBlockRecordQueryDTO;
import com.stamping.pad.dto.LayerBlockReleaseDTO;
import com.stamping.pad.entity.LayerBlockRecord;
import com.stamping.pad.service.LayerBlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/layer-block")
@RequiredArgsConstructor
public class LayerBlockController {

    private final LayerBlockService layerBlockService;

    @GetMapping("/page")
    public Result<Page<LayerBlockRecord>> page(LayerBlockRecordQueryDTO query) {
        return Result.success(layerBlockService.pageList(query));
    }

    @GetMapping("/active")
    public Result<List<LayerBlockRecord>> listActive() {
        return Result.success(layerBlockService.listActiveBlocks());
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.success(layerBlockService.statistics());
    }

    @PostMapping
    public Result<LayerBlockRecord> block(@RequestBody LayerBlockDTO dto) {
        return Result.success(layerBlockService.block(dto));
    }

    @PostMapping("/release")
    public Result<LayerBlockRecord> release(@RequestBody LayerBlockReleaseDTO dto) {
        return Result.success(layerBlockService.release(dto));
    }
}
