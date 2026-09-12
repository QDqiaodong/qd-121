package com.stamping.pad.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.dto.MaintenanceRecordQueryDTO;
import com.stamping.pad.dto.PadMaintenanceDTO;
import com.stamping.pad.entity.LayerAdjustRecord;
import com.stamping.pad.entity.PadInfo;
import com.stamping.pad.entity.PadMaintenanceRecord;
import com.stamping.pad.mapper.LayerAdjustRecordMapper;
import com.stamping.pad.mapper.PadInfoMapper;
import com.stamping.pad.mapper.PadMaintenanceRecordMapper;
import com.stamping.pad.vo.PadMaintenanceDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PadMaintenanceService {

    /** 可用、待检、停用 */
    public static final Set<String> STATUS_SET = Set.of("AVAILABLE", "PENDING", "DISABLED");
    /** 正常、已修复、异常待处理、报废建议 */
    private static final Set<String> RESULT_SET = Set.of("NORMAL", "REPAIRED", "ABNORMAL", "SCRAPPED");
    /** 登记为待检/停用时需自动离架，不再占用层位配额 */
    private static final Set<String> OFF_SHELF_STATUS = Set.of("PENDING", "DISABLED");
    /** 报废终态：已报废出库的垫板档案冻结，不再接受保养登记 */
    public static final String SCRAPPED = "SCRAPPED";

    private final PadMaintenanceRecordMapper maintenanceRecordMapper;
    private final PadInfoMapper padInfoMapper;
    private final LayerAdjustRecordMapper adjustRecordMapper;

    public Page<PadMaintenanceRecord> pageList(MaintenanceRecordQueryDTO query) {
        Page<PadMaintenanceRecord> page = new Page<>(query.getPageNum(), query.getPageSize());
        return maintenanceRecordMapper.selectPageList(page, query);
    }

    public PadMaintenanceRecord getById(Long id) {
        return maintenanceRecordMapper.selectById(id);
    }

    public List<PadMaintenanceRecord> listByPadId(Long padId) {
        return maintenanceRecordMapper.selectListByPadId(padId);
    }

    /** 详情：档案 + 最近一次保养 + 状态变更记录 */
    public PadMaintenanceDetailVO detail(Long padId) {
        PadInfo pad = padInfoMapper.selectById(padId);
        if (pad == null) {
            throw new RuntimeException("垫板不存在");
        }
        List<PadMaintenanceRecord> all = maintenanceRecordMapper.selectListByPadId(padId);
        PadMaintenanceDetailVO vo = new PadMaintenanceDetailVO();
        vo.setPad(pad);
        vo.setMaintenanceRecords(all);
        vo.setLatestRecord(all.isEmpty() ? null : all.get(0));
        vo.setStatusChangeRecords(all.stream()
                .filter(r -> r.getStatusBefore() == null
                        || !r.getStatusBefore().equals(r.getStatusAfter()))
                .toList());
        return vo;
    }

    /**
     * 登记保养：写入保养记录，并将垫板标记为对应状态（可用/待检/停用）。
     * 保养记录的 status_before/status_after 用于详情页的状态变更时间线。
     * 标记为待检/停用时，若垫板仍在架则自动离架（shelf_layer_code/bind_time 置空并写入
     * UNBIND 调整记录），层位占用与配额校验不再计入该垫板；恢复可用后需重新绑定或归还上架。
     */
    @Transactional(rollbackFor = Exception.class)
    public PadMaintenanceRecord register(PadMaintenanceDTO dto) {
        PadInfo pad = padInfoMapper.selectById(dto.getPadId());
        if (pad == null) {
            throw new RuntimeException("垫板不存在");
        }
        if (SCRAPPED.equals(pad.getMaintenanceStatus())) {
            throw new RuntimeException("垫板【" + pad.getPadCode() + "】已报废出库，档案冻结，禁止再登记保养");
        }
        String statusAfter = dto.getStatusAfter() == null ? "" : dto.getStatusAfter().trim();
        if (!STATUS_SET.contains(statusAfter)) {
            throw new RuntimeException("保养后状态非法，仅支持：可用、待检、停用");
        }
        String result = dto.getMaintenanceResult() == null ? "" : dto.getMaintenanceResult().trim();
        if (!RESULT_SET.contains(result)) {
            throw new RuntimeException("保养结果非法");
        }
        String maintenanceType = dto.getMaintenanceType() == null ? "" : dto.getMaintenanceType().trim();
        if (maintenanceType.isEmpty()) {
            throw new RuntimeException("保养类型不能为空");
        }
        String handler = dto.getHandler() == null ? "" : dto.getHandler().trim();
        if (handler.isEmpty()) {
            throw new RuntimeException("处理人不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime maintenanceTime = dto.getMaintenanceTime() != null ? dto.getMaintenanceTime() : now;

        PadMaintenanceRecord record = new PadMaintenanceRecord();
        record.setPadId(pad.getId());
        record.setPadCode(pad.getPadCode());
        record.setMaintenanceType(maintenanceType);
        record.setHandler(handler);
        record.setMaintenanceTime(maintenanceTime);
        record.setMaintenanceResult(result);
        record.setStatusBefore(pad.getMaintenanceStatus() == null ? "AVAILABLE" : pad.getMaintenanceStatus());
        record.setStatusAfter(statusAfter);
        record.setRemark(dto.getRemark());
        record.setCreateTime(now);
        record.setUpdateTime(now);
        maintenanceRecordMapper.insert(record);

        if (!statusAfter.equals(pad.getMaintenanceStatus())) {
            pad.setMaintenanceStatus(statusAfter);
            pad.setUpdateTime(now);
            padInfoMapper.updateById(pad);
        }

        // 待检/停用垫板不再计入层位占用：仍在架时自动离架，避免已满层无法归还上架、
        // 下调配额被卡住；离架后占用数与档案绑定一致，不留下无主占用
        if (OFF_SHELF_STATUS.contains(statusAfter)
                && pad.getShelfLayerCode() != null && !pad.getShelfLayerCode().isEmpty()) {
            String originLayerCode = pad.getShelfLayerCode();
            // 显式 set null，避免 updateById 忽略空字段
            LambdaUpdateWrapper<PadInfo> offShelf = new LambdaUpdateWrapper<>();
            offShelf.eq(PadInfo::getId, pad.getId())
                    .set(PadInfo::getShelfLayerCode, null)
                    .set(PadInfo::getBindTime, null)
                    .set(PadInfo::getUpdateTime, now);
            padInfoMapper.update(null, offShelf);
            pad.setShelfLayerCode(null);
            pad.setBindTime(null);

            LayerAdjustRecord adjustRecord = new LayerAdjustRecord();
            adjustRecord.setPadId(pad.getId());
            adjustRecord.setPadCode(pad.getPadCode());
            adjustRecord.setOldLayerCode(originLayerCode);
            adjustRecord.setNewLayerCode(null);
            adjustRecord.setAdjustType("UNBIND");
            adjustRecord.setOperator(handler);
            adjustRecord.setAdjustReason("保养登记为" + statusLabel(statusAfter) + "，垫板自动离架");
            adjustRecord.setAdjustTime(now);
            adjustRecordMapper.insert(adjustRecord);
        }
        return record;
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "PENDING" -> "待检";
            case "DISABLED" -> "停用";
            default -> "可用";
        };
    }

    public Map<String, Object> statistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("availableCount", countByStatus("AVAILABLE"));
        stats.put("pendingCount", countByStatus("PENDING"));
        stats.put("disabledCount", countByStatus("DISABLED"));
        stats.put("scrappedCount", countByStatus(SCRAPPED));
        return stats;
    }

    private Long countByStatus(String status) {
        return padInfoMapper.selectCount(
                new LambdaQueryWrapper<PadInfo>().eq(PadInfo::getMaintenanceStatus, status));
    }
}
