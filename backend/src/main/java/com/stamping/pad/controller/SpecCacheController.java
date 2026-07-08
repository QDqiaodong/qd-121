package com.stamping.pad.controller;

import com.stamping.pad.common.Result;
import com.stamping.pad.service.PadSpecCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/spec")
@RequiredArgsConstructor
public class SpecCacheController {

    private final PadSpecCacheService specCacheService;

    @GetMapping("/list")
    public Result<Map<String, Object>> listAll() {
        return Result.success(specCacheService.getAllSpecs());
    }

    @GetMapping("/keys")
    public Result<Set<String>> listKeys() {
        return Result.success(specCacheService.getAllKeys());
    }

    @GetMapping("/{key}")
    public Result<Object> getByKey(@PathVariable String key) {
        return Result.success(specCacheService.getSpec(key));
    }

    @PostMapping
    public Result<Void> addSpec(@RequestParam String key, @RequestParam String value) {
        specCacheService.addSpec(key, value);
        return Result.success();
    }

    @DeleteMapping("/{key}")
    public Result<Void> deleteSpec(@PathVariable String key) {
        specCacheService.deleteSpec(key);
        return Result.success();
    }
}
