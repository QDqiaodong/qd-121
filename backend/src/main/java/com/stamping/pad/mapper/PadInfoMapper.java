package com.stamping.pad.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.dto.PadQueryDTO;
import com.stamping.pad.entity.PadInfo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PadInfoMapper extends BaseMapper<PadInfo> {

    Page<PadInfo> selectPageList(Page<PadInfo> page, @Param("query") PadQueryDTO query);

    List<PadInfo> selectByLayerCode(@Param("layerCode") String layerCode);

    List<PadInfo> selectByShelfCode(@Param("shelfCode") String shelfCode);

    /** 按主键集合查询垫板（预留登记批量校验，顺序以入参集合为准由调用方处理） */
    List<PadInfo> selectByIds(@Param("ids") java.util.Collection<Long> ids);

    /** 预留登记校验前对垫板行加锁：串行化同一块垫板的预留登记与领用/解绑并发 */
    PadInfo lockById(@Param("id") Long id);
}
