package com.stamping.pad.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.dto.MaintenanceRecordQueryDTO;
import com.stamping.pad.entity.PadMaintenanceRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PadMaintenanceRecordMapper extends BaseMapper<PadMaintenanceRecord> {

    Page<PadMaintenanceRecord> selectPageList(Page<PadMaintenanceRecord> page,
                                              @Param("query") MaintenanceRecordQueryDTO query);

    List<PadMaintenanceRecord> selectListByPadId(@Param("padId") Long padId);
}
