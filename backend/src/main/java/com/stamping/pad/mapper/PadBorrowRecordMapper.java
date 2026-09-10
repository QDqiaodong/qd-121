package com.stamping.pad.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.dto.BorrowRecordQueryDTO;
import com.stamping.pad.entity.PadBorrowRecord;
import org.apache.ibatis.annotations.Param;

public interface PadBorrowRecordMapper extends BaseMapper<PadBorrowRecord> {

    Page<PadBorrowRecord> selectPageList(Page<PadBorrowRecord> page, @Param("query") BorrowRecordQueryDTO query);

    PadBorrowRecord selectDetailById(@Param("id") Long id);
}
