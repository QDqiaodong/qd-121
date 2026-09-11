package com.stamping.pad.vo;

import com.stamping.pad.entity.PadInfo;
import com.stamping.pad.entity.PadMaintenanceRecord;
import lombok.Data;

import java.util.List;

/**
 * 垫板保养详情：档案信息 + 最近一次保养 + 状态变更记录（保养记录中状态发生变化的部分）。
 */
@Data
public class PadMaintenanceDetailVO {

    private PadInfo pad;

    private PadMaintenanceRecord latestRecord;

    private List<PadMaintenanceRecord> statusChangeRecords;

    private List<PadMaintenanceRecord> maintenanceRecords;
}
