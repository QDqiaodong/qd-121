package com.stamping.pad.controller;

import com.stamping.pad.common.Result;
import com.stamping.pad.entity.ShelfLayer;
import com.stamping.pad.service.ShelfLayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/shelf-layer")
@RequiredArgsConstructor
public class ShelfLayerController {

    private final ShelfLayerService shelfLayerService;

    @GetMapping("/list")
    public Result<List<ShelfLayer>> listAll() {
        return Result.success(shelfLayerService.listAll());
    }

    @GetMapping("/group")
    public Result<List<ShelfLayer>> listGroupByShelf() {
        return Result.success(shelfLayerService.listGroupByShelf());
    }

    @GetMapping("/{id}")
    public Result<ShelfLayer> getById(@PathVariable Long id) {
        return Result.success(shelfLayerService.getById(id));
    }

    @GetMapping("/code/{layerCode}")
    public Result<ShelfLayer> getByCode(@PathVariable String layerCode) {
        return Result.success(shelfLayerService.getByCode(layerCode));
    }

    @PostMapping
    public Result<ShelfLayer> save(@RequestBody ShelfLayer shelfLayer) {
        return Result.success(shelfLayerService.save(shelfLayer));
    }

    @PutMapping
    public Result<ShelfLayer> update(@RequestBody ShelfLayer shelfLayer) {
        return Result.success(shelfLayerService.save(shelfLayer));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        shelfLayerService.delete(id);
        return Result.success();
    }
}
