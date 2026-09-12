package com.stamping.pad.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.dto.PadMoldReserveQueryDTO;
import com.stamping.pad.entity.PadMoldReserveRecord;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PadMoldReserveRecordMapper extends BaseMapper<PadMoldReserveRecord> {

    Page<PadMoldReserveRecord> selectPageList(Page<PadMoldReserveRecord> page,
                                              @Param("query") PadMoldReserveQueryDTO query);

    PadMoldReserveRecord selectDetailById(@Param("id") Long id);

    /** 惰性状态迁移：到开始时间的待生效记录置为生效中 */
    int activatePendingReserves(@Param("now") LocalDateTime now);

    /** 惰性状态迁移：超过结束时间的待生效/生效中记录置为已到期，系统补写释放信息 */
    int expireDueReserves(@Param("now") LocalDateTime now);
}
