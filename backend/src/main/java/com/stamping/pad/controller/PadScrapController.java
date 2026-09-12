package com.stamping.pad.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.common.Result;
import com.stamping.pad.dto.PadScrapDTO;
import com.stamping.pad.dto.ScrapRecordQueryDTO;
import com.stamping.pad.entity.PadScrapRecord;
import com.stamping.pad.service.PadScrapService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/scrap")
@RequiredArgsConstructor
public class PadScrapController {

    private final PadScrapService padScrapService;

    @GetMapping("/page")
    public Result<Page<PadScrapRecord>> page(ScrapRecordQueryDTO query) {
        return Result.success(padScrapService.pageList(query));
    }

    @GetMapping("/{id}")
    public Result<PadScrapRecord> getById(@PathVariable Long id) {
        return Result.success(padScrapService.getById(id));
    }

    @GetMapping("/pad/{padId}")
    public Result<List<PadScrapRecord>> listByPadId(@PathVariable Long padId) {
        return Result.success(padScrapService.listByPadId(padId));
    }

    /** 报废出库候选垫板（未报废）与已有报废建议的垫板ID集合，前端据此禁用并提示，后端二次校验 */
    @GetMapping("/outbound-candidates")
    public Result<Map<String, Object>> outboundCandidates() {
        return Result.success(padScrapService.outboundCandidates());
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.success(padScrapService.statistics());
    }

    @PostMapping("/outbound")
    public Result<PadScrapRecord> outbound(@Valid @RequestBody PadScrapDTO dto) {
        return Result.success(padScrapService.outbound(dto));
    }
}
