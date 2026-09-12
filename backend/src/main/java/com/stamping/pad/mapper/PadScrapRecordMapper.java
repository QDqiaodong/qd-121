package com.stamping.pad.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.dto.ScrapRecordQueryDTO;
import com.stamping.pad.entity.PadScrapRecord;
import org.apache.ibatis.annotations.Param;

public interface PadScrapRecordMapper extends BaseMapper<PadScrapRecord> {

    Page<PadScrapRecord> selectPageList(Page<PadScrapRecord> page,
                                        @Param("query") ScrapRecordQueryDTO query);

    PadScrapRecord selectDetailById(@Param("id") Long id);
}
