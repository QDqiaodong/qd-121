package com.stamping.pad.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stamping.pad.entity.PadMoldReserveItem;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

public interface PadMoldReserveItemMapper extends BaseMapper<PadMoldReserveItem> {

    /** 预留单详情：明细垫板当前层位与保养状态联表 pad_info 实时带出 */
    List<PadMoldReserveItem> selectByReserveId(@Param("reserveId") Long reserveId);

    /** 批量回填预留单的明细清单 */
    List<PadMoldReserveItem> selectByReserveIds(@Param("reserveIds") Collection<Long> reserveIds);
}
