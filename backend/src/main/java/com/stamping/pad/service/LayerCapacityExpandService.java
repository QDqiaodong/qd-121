package com.stamping.pad.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.dto.LayerCapacityExpandDTO;
import com.stamping.pad.dto.LayerCapacityExpandFinishDTO;
import com.stamping.pad.dto.LayerCapacityExpandQueryDTO;
import com.stamping.pad.entity.LayerCapacityExpandRecord;
import com.stamping.pad.entity.ShelfLayer;
import com.stamping.pad.mapper.LayerCapacityExpandRecordMapper;
import com.stamping.pad.mapper.ShelfLayerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LayerCapacityExpandService {

    private final LayerCapacityExpandRecordMapper expandRecordMapper;
    private final ShelfLayerMapper shelfLayerMapper;

    public Page<LayerCapacityExpandRecord> pageList(LayerCapacityExpandQueryDTO query) {
        // 回看台账前先惰性刷新状态：到期待生效/生效中记录自动落为已到期
        refreshStatus();
        Page<LayerCapacityExpandRecord> page = new Page<>(query.getPageNum(), query.getPageSize());
        return expandRecordMapper.selectPageList(page, query);
    }

    /** 当前实际生效的扩容记录：层位列表/下拉标注扩容状态与新配额 */
    public List<LayerCapacityExpandRecord> listEffectiveExpansions() {
        refreshStatus();
        return expandRecordMapper.selectEffectiveExpansions(LocalDateTime.now());
    }

    /**
     * 惰性状态迁移：待生效到点开始生效，超过结束时间自动到期并补写系统结束信息。
     * 容量校验本身按生效时段实时判断，状态列仅用于台账展示与筛选，二者最终一致。
     */
    public void refreshStatus() {
        LocalDateTime now = LocalDateTime.now();
        expandRecordMapper.activatePendingExpansions(now);
        expandRecordMapper.expireDueExpansions(now);
    }

    /**
     * 登记临时扩容：旺季到货等原因时登记新配额、生效时段与经办人。
     * 同一层位同一时间仅允许一条“待生效/生效中”记录；行锁层位（与上架占用同一把锁），
     * 防止扩容登记与绑定/归还/导入并发交错导致配额口径不一致。
     */
    @Transactional(rollbackFor = Exception.class)
    public LayerCapacityExpandRecord register(LayerCapacityExpandDTO dto) {
        String layerCode = dto.getLayerCode() == null ? "" : dto.getLayerCode().trim();
        if (layerCode.isEmpty()) {
            throw new RuntimeException("请选择要扩容的层位");
        }
        String expandReason = dto.getExpandReason() == null ? "" : dto.getExpandReason().trim();
        if (expandReason.isEmpty()) {
            throw new RuntimeException("扩容原因不能为空");
        }
        String operator = dto.getOperator() == null ? "" : dto.getOperator().trim();
        if (operator.isEmpty()) {
            throw new RuntimeException("经办人不能为空");
        }
        if (dto.getEndTime() == null) {
            throw new RuntimeException("生效结束时间不能为空，到期后自动回到原配额");
        }

        // 行锁层位：与 lockAndAssertCapacity 串行化，扩容生效后该层立即按新配额校验
        ShelfLayer layer = shelfLayerMapper.lockByLayerCode(layerCode);
        if (layer == null) {
            throw new RuntimeException("货架分层不存在");
        }
        int originalCapacity = layer.getCapacity() == null ? 0 : layer.getCapacity();
        if (dto.getExpandCapacity() == null || dto.getExpandCapacity() <= originalCapacity) {
            throw new RuntimeException("扩容后配额必须大于原配额（当前 " + originalCapacity + "）");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = dto.getStartTime() != null ? dto.getStartTime() : now;
        if (!dto.getEndTime().isAfter(startTime)) {
            throw new RuntimeException("生效结束时间必须晚于生效开始时间");
        }
        if (!dto.getEndTime().isAfter(now)) {
            throw new RuntimeException("生效结束时间必须晚于当前时间，已过期时段无需登记");
        }

        Long activeCount = expandRecordMapper.selectCount(
                new LambdaQueryWrapper<LayerCapacityExpandRecord>()
                        .eq(LayerCapacityExpandRecord::getLayerCode, layerCode)
                        .in(LayerCapacityExpandRecord::getStatus,
                                LayerCapacityExpandRecord.STATUS_PENDING,
                                LayerCapacityExpandRecord.STATUS_ACTIVE));
        if (activeCount > 0) {
            throw new RuntimeException("层位【" + layerCode + "】已存在生效中的扩容，请先结束或待其到期后再登记");
        }

        LayerCapacityExpandRecord record = new LayerCapacityExpandRecord();
        record.setLayerId(layer.getId());
        record.setLayerCode(layerCode);
        record.setExpandReason(expandReason);
        record.setOriginalCapacity(originalCapacity);
        record.setExpandCapacity(dto.getExpandCapacity());
        record.setStartTime(startTime);
        record.setEndTime(dto.getEndTime());
        record.setOperator(operator);
        record.setStatus(startTime.isAfter(now)
                ? LayerCapacityExpandRecord.STATUS_PENDING
                : LayerCapacityExpandRecord.STATUS_ACTIVE);
        record.setRemark(dto.getRemark());
        record.setCreateTime(now);
        record.setUpdateTime(now);
        expandRecordMapper.insert(record);
        return record;
    }

    /**
     * 提前结束扩容：必须填写结束结论；仅“待生效/生效中”记录可结束，条件更新防止并发重复结束。
     * 结束后层位立即回到原配额，超出原配额的在架垫板保留但不可再上架新垫板。
     */
    @Transactional(rollbackFor = Exception.class)
    public LayerCapacityExpandRecord finish(LayerCapacityExpandFinishDTO dto) {
        if (dto.getId() == null) {
            throw new RuntimeException("扩容记录不存在");
        }
        LayerCapacityExpandRecord record = expandRecordMapper.selectById(dto.getId());
        if (record == null) {
            throw new RuntimeException("扩容记录不存在");
        }
        if (!LayerCapacityExpandRecord.STATUS_PENDING.equals(record.getStatus())
                && !LayerCapacityExpandRecord.STATUS_ACTIVE.equals(record.getStatus())) {
            throw new RuntimeException("该扩容记录已结束，禁止重复操作");
        }
        String conclusion = dto.getFinishConclusion() == null ? "" : dto.getFinishConclusion().trim();
        if (conclusion.isEmpty()) {
            throw new RuntimeException("提前结束扩容必须填写结论");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime finishTime = dto.getFinishTime() != null ? dto.getFinishTime() : now;
        if (finishTime.isBefore(record.getStartTime())) {
            throw new RuntimeException("结束时间不能早于生效开始时间");
        }
        String finishOperator = dto.getFinishOperator() == null ? "" : dto.getFinishOperator().trim();
        if (finishOperator.isEmpty()) {
            finishOperator = record.getOperator();
        }

        // 条件更新：仅待生效/生效中记录可被结束，并发下重复结束直接失败
        LambdaUpdateWrapper<LayerCapacityExpandRecord> update = new LambdaUpdateWrapper<>();
        update.eq(LayerCapacityExpandRecord::getId, record.getId())
                .in(LayerCapacityExpandRecord::getStatus,
                        LayerCapacityExpandRecord.STATUS_PENDING,
                        LayerCapacityExpandRecord.STATUS_ACTIVE)
                .set(LayerCapacityExpandRecord::getStatus, LayerCapacityExpandRecord.STATUS_ENDED)
                .set(LayerCapacityExpandRecord::getFinishTime, finishTime)
                .set(LayerCapacityExpandRecord::getFinishConclusion, conclusion)
                .set(LayerCapacityExpandRecord::getFinishOperator, finishOperator)
                .set(LayerCapacityExpandRecord::getUpdateTime, now);
        int rows = expandRecordMapper.update(null, update);
        if (rows == 0) {
            throw new RuntimeException("该扩容记录已结束，禁止重复操作");
        }
        return expandRecordMapper.selectById(record.getId());
    }

    public Map<String, Object> statistics() {
        refreshStatus();
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeCount", expandRecordMapper.selectCount(
                new LambdaQueryWrapper<LayerCapacityExpandRecord>()
                        .eq(LayerCapacityExpandRecord::getStatus, LayerCapacityExpandRecord.STATUS_ACTIVE)));
        stats.put("pendingCount", expandRecordMapper.selectCount(
                new LambdaQueryWrapper<LayerCapacityExpandRecord>()
                        .eq(LayerCapacityExpandRecord::getStatus, LayerCapacityExpandRecord.STATUS_PENDING)));
        stats.put("expiredCount", expandRecordMapper.selectCount(
                new LambdaQueryWrapper<LayerCapacityExpandRecord>()
                        .eq(LayerCapacityExpandRecord::getStatus, LayerCapacityExpandRecord.STATUS_EXPIRED)));
        stats.put("endedCount", expandRecordMapper.selectCount(
                new LambdaQueryWrapper<LayerCapacityExpandRecord>()
                        .eq(LayerCapacityExpandRecord::getStatus, LayerCapacityExpandRecord.STATUS_ENDED)));
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        stats.put("monthExpandCount", expandRecordMapper.selectCount(
                new LambdaQueryWrapper<LayerCapacityExpandRecord>()
                        .ge(LayerCapacityExpandRecord::getStartTime, monthStart)));
        return stats;
    }
}
