package com.stamping.pad.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.dto.LayerCapacityExpandQueryDTO;
import com.stamping.pad.entity.LayerCapacityExpandRecord;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LayerCapacityExpandRecordMapper extends BaseMapper<LayerCapacityExpandRecord> {

    Page<LayerCapacityExpandRecord> selectPageList(Page<LayerCapacityExpandRecord> page,
                                                   @Param("query") LayerCapacityExpandQueryDTO query);

    /** 当前时刻实际生效的扩容记录（按生效时段判断，与状态列惰性刷新解耦）：容量校验与层位列表回填共用 */
    List<LayerCapacityExpandRecord> selectEffectiveExpansions(@Param("now") LocalDateTime now);

    /** 指定层位当前时刻实际生效的扩容记录，无则返回 null */
    LayerCapacityExpandRecord selectEffectiveExpansion(@Param("layerCode") String layerCode,
                                                       @Param("now") LocalDateTime now);

    /** 惰性状态迁移：到开始时间的待生效记录置为生效中 */
    int activatePendingExpansions(@Param("now") LocalDateTime now);

    /** 惰性状态迁移：超过结束时间的待生效/生效中记录置为已到期，系统补写结束信息 */
    int expireDueExpansions(@Param("now") LocalDateTime now);
}
