package com.stamping.pad.vo;

import com.stamping.pad.entity.PadInventoryItem;
import com.stamping.pad.entity.PadInventorySheet;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 点检单详情：单据信息 + 覆盖层位实时占用（在架数/配额）+ 明细列表。
 * 层位占用始终实时统计 pad_info.shelf_layer_code，刷新后与垫板在架占用、层位占用口径一致。
 */
@Data
public class PadInventoryDetailVO {

    private PadInventorySheet sheet;

    /** layerCode -> 层位实时信息（layerName/padCount/capacity） */
    private List<Map<String, Object>> layers;

    private List<PadInventoryItem> items;
}
