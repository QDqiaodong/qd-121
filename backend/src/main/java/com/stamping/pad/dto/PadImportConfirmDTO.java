package com.stamping.pad.dto;

import com.stamping.pad.vo.PadImportRowVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量导入确认：携带预览阶段校验通过的行数据，后端会再次逐行校验后落库。
 */
@Data
public class PadImportConfirmDTO {

    @Valid
    @NotEmpty(message = "没有可导入的数据行")
    private List<PadImportRowVO> rows;
}
