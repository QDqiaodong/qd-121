package com.stamping.pad.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.common.Result;
import com.stamping.pad.dto.BorrowRecordQueryDTO;
import com.stamping.pad.dto.PadCheckoutDTO;
import com.stamping.pad.dto.PadReturnDTO;
import com.stamping.pad.entity.PadBorrowRecord;
import com.stamping.pad.entity.ShelfLayer;
import com.stamping.pad.service.PadBorrowService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/borrow")
@RequiredArgsConstructor
public class PadBorrowController {

    private final PadBorrowService padBorrowService;

    @GetMapping("/page")
    public Result<Page<PadBorrowRecord>> page(BorrowRecordQueryDTO query) {
        return Result.success(padBorrowService.pageList(query));
    }

    @GetMapping("/{id}")
    public Result<PadBorrowRecord> getById(@PathVariable Long id) {
        return Result.success(padBorrowService.getById(id));
    }

    @GetMapping("/pad/{padId}")
    public Result<List<PadBorrowRecord>> listByPadId(@PathVariable Long padId) {
        return Result.success(padBorrowService.listByPadId(padId));
    }

    @GetMapping("/available-layers")
    public Result<List<ShelfLayer>> availableLayers() {
        return Result.success(padBorrowService.listAvailableReturnLayers());
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.success(padBorrowService.statistics());
    }

    @GetMapping("/export")
    public void export(BorrowRecordQueryDTO query, HttpServletResponse response) throws IOException {
        padBorrowService.exportRecords(query, response);
    }

    @PostMapping("/checkout")
    public Result<PadBorrowRecord> checkout(@Valid @RequestBody PadCheckoutDTO dto) {
        return Result.success(padBorrowService.checkout(dto));
    }

    @PostMapping("/return")
    public Result<PadBorrowRecord> doReturn(@Valid @RequestBody PadReturnDTO dto) {
        return Result.success(padBorrowService.doReturn(dto));
    }
}
