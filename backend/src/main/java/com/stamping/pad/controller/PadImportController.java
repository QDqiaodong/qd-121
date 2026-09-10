package com.stamping.pad.controller;

import com.stamping.pad.common.Result;
import com.stamping.pad.dto.PadImportConfirmDTO;
import com.stamping.pad.service.PadImportService;
import com.stamping.pad.vo.PadImportResultVO;
import com.stamping.pad.vo.PadImportRowVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/pad/import")
@RequiredArgsConstructor
public class PadImportController {

    private final PadImportService padImportService;

    /**
     * 上传 Excel：解析并逐行校验，返回预览数据（含每行错误标记），不落库。
     */
    @PostMapping("/preview")
    public Result<List<PadImportRowVO>> preview(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.success(padImportService.preview(file));
    }

    /**
     * 确认导入：仅将合格行落库，返回成功与失败明细。
     */
    @PostMapping("/confirm")
    public Result<PadImportResultVO> confirm(@Valid @RequestBody PadImportConfirmDTO dto) {
        return Result.success(padImportService.confirmImport(dto.getRows()));
    }

    /**
     * 下载导入模板。
     */
    @GetMapping("/template")
    public void template(HttpServletResponse response) throws IOException {
        padImportService.downloadTemplate(response);
    }
}
