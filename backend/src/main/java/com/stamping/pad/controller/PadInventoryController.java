package com.stamping.pad.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.common.Result;
import com.stamping.pad.dto.InventoryExtraItemDTO;
import com.stamping.pad.dto.InventoryItemMarkDTO;
import com.stamping.pad.dto.InventorySheetCloseDTO;
import com.stamping.pad.dto.InventorySheetCreateDTO;
import com.stamping.pad.dto.InventorySheetQueryDTO;
import com.stamping.pad.dto.InventorySheetSubmitDTO;
import com.stamping.pad.entity.PadInventoryItem;
import com.stamping.pad.entity.PadInventorySheet;
import com.stamping.pad.service.PadInventoryService;
import com.stamping.pad.vo.PadInventoryDetailVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class PadInventoryController {

    private final PadInventoryService padInventoryService;

    @GetMapping("/page")
    public Result<Page<PadInventorySheet>> page(InventorySheetQueryDTO query) {
        return Result.success(padInventoryService.pageList(query));
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.success(padInventoryService.statistics());
    }

    @GetMapping("/{id}")
    public Result<PadInventoryDetailVO> detail(@PathVariable Long id) {
        return Result.success(padInventoryService.getDetail(id));
    }

    @PostMapping
    public Result<PadInventorySheet> create(@Valid @RequestBody InventorySheetCreateDTO dto) {
        return Result.success(padInventoryService.create(dto));
    }

    @PostMapping("/item/mark")
    public Result<PadInventoryItem> markItem(@Valid @RequestBody InventoryItemMarkDTO dto) {
        return Result.success(padInventoryService.markItem(dto));
    }

    @PostMapping("/{sheetId}/extra")
    public Result<PadInventoryItem> addExtraItem(@PathVariable Long sheetId,
                                                 @Valid @RequestBody InventoryExtraItemDTO dto) {
        return Result.success(padInventoryService.addExtraItem(sheetId, dto));
    }

    @DeleteMapping("/item/{itemId}")
    public Result<Void> removeExtraItem(@PathVariable Long itemId) {
        padInventoryService.removeExtraItem(itemId);
        return Result.success();
    }

    @PostMapping("/submit")
    public Result<PadInventorySheet> submit(@Valid @RequestBody InventorySheetSubmitDTO dto) {
        return Result.success(padInventoryService.submit(dto));
    }

    @PostMapping("/close")
    public Result<PadInventorySheet> close(@Valid @RequestBody InventorySheetCloseDTO dto) {
        return Result.success(padInventoryService.close(dto));
    }

    @PostMapping("/{id}/cancel")
    public Result<PadInventorySheet> cancel(@PathVariable Long id) {
        return Result.success(padInventoryService.cancel(id));
    }
}
