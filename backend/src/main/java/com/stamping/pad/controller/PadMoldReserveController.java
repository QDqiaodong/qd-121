package com.stamping.pad.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.common.Result;
import com.stamping.pad.dto.PadMoldReserveDTO;
import com.stamping.pad.dto.PadMoldReserveQueryDTO;
import com.stamping.pad.dto.PadMoldReserveReleaseDTO;
import com.stamping.pad.entity.PadMoldReserveRecord;
import com.stamping.pad.service.PadMoldReserveService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/pad-reserve")
@RequiredArgsConstructor
public class PadMoldReserveController {

    private final PadMoldReserveService padMoldReserveService;

    @GetMapping("/page")
    public Result<Page<PadMoldReserveRecord>> page(PadMoldReserveQueryDTO query) {
        return Result.success(padMoldReserveService.pageList(query));
    }

    @GetMapping("/{id}")
    public Result<PadMoldReserveRecord> getById(@PathVariable Long id) {
        return Result.success(padMoldReserveService.getById(id));
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.success(padMoldReserveService.statistics());
    }

    @PostMapping
    public Result<PadMoldReserveRecord> register(@RequestBody PadMoldReserveDTO dto) {
        return Result.success(padMoldReserveService.register(dto));
    }

    @PostMapping("/release")
    public Result<PadMoldReserveRecord> release(@RequestBody PadMoldReserveReleaseDTO dto) {
        return Result.success(padMoldReserveService.release(dto));
    }
}
