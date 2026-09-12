package com.stamping.pad.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.dto.PadMoldReserveDTO;
import com.stamping.pad.dto.PadMoldReserveQueryDTO;
import com.stamping.pad.dto.PadMoldReserveReleaseDTO;
import com.stamping.pad.entity.PadInfo;
import com.stamping.pad.entity.PadMoldReserveItem;
import com.stamping.pad.entity.PadMoldReserveRecord;
import com.stamping.pad.mapper.PadInfoMapper;
import com.stamping.pad.mapper.PadMoldReserveItemMapper;
import com.stamping.pad.mapper.PadMoldReserveRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 换模垫板预留：换模前库房把指定在架垫板预留给即将上线的模具。
 * 登记后至到期前（含待生效时段）这些垫板不能被其他产线领用，也不能解绑/换层；
 * 到期自动释放，未到期可手工释放，释放后垫板立即恢复可领。
 */
@Service
@RequiredArgsConstructor
public class PadMoldReserveService {

    private final PadMoldReserveRecordMapper reserveRecordMapper;
    private final PadMoldReserveItemMapper reserveItemMapper;
    private final PadInfoMapper padInfoMapper;

    public Page<PadMoldReserveRecord> pageList(PadMoldReserveQueryDTO query) {
        // 回看台账前先惰性刷新状态：到期待生效/生效中记录自动落为已到期
        refreshStatus();
        Page<PadMoldReserveRecord> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<PadMoldReserveRecord> result = reserveRecordMapper.selectPageList(page, query);
        fillItems(result.getRecords());
        return result;
    }

    public PadMoldReserveRecord getById(Long id) {
        PadMoldReserveRecord record = reserveRecordMapper.selectDetailById(id);
        if (record != null) {
            record.setItems(reserveItemMapper.selectByReserveId(id));
        }
        return record;
    }

    /** 批量回填预留板清单（含垫板当前层位与保养状态，供台账回看对照） */
    private void fillItems(List<PadMoldReserveRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> ids = records.stream().map(PadMoldReserveRecord::getId).toList();
        Map<Long, List<PadMoldReserveItem>> itemMap = reserveItemMapper.selectByReserveIds(ids).stream()
                .collect(Collectors.groupingBy(PadMoldReserveItem::getReserveId));
        records.forEach(r -> r.setItems(itemMap.getOrDefault(r.getId(), List.of())));
    }

    /**
     * 惰性状态迁移：待生效到点开始生效，超过结束时间自动到期并补写系统释放信息。
     * 领用/解绑约束本身按生效时段实时判断，状态列仅用于台账展示与筛选，二者最终一致。
     */
    public void refreshStatus() {
        LocalDateTime now = LocalDateTime.now();
        reserveRecordMapper.activatePendingReserves(now);
        reserveRecordMapper.expireDueReserves(now);
    }

    /**
     * 登记换模预留：登记模具、预留板清单、生效时段与经办人。
     * 预留板必须在架且可用（待检/停用/已报废/领用离架的垫板不可预留）；
     * 同一垫板在时段重叠的待生效/生效中预留中不可重复登记。
     * 逐块行锁垫板（与领用/解绑同一把垫板行锁），防止预留登记与领用/解绑并发交错。
     */
    @Transactional(rollbackFor = Exception.class)
    public PadMoldReserveRecord register(PadMoldReserveDTO dto) {
        String moldCode = trim(dto.getMoldCode());
        if (moldCode.isEmpty()) {
            throw new RuntimeException("模具编码不能为空");
        }
        String operator = trim(dto.getOperator());
        if (operator.isEmpty()) {
            throw new RuntimeException("经办人不能为空");
        }
        if (dto.getEndTime() == null) {
            throw new RuntimeException("预留结束时间不能为空，到期后垫板自动恢复可领");
        }
        List<Long> padIds = dto.getPadIds() == null ? List.of()
                : dto.getPadIds().stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (padIds.isEmpty()) {
            throw new RuntimeException("请至少选择一块预留垫板");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = dto.getStartTime() != null ? dto.getStartTime() : now;
        if (!dto.getEndTime().isAfter(startTime)) {
            throw new RuntimeException("预留结束时间必须晚于预留开始时间");
        }
        if (!dto.getEndTime().isAfter(now)) {
            throw new RuntimeException("预留结束时间必须晚于当前时间，已过期时段无需登记");
        }

        // 按ID升序逐块行锁垫板：与领用/解绑串行化，避免死锁
        List<Long> sortedIds = padIds.stream().sorted().toList();
        List<PadInfo> pads = new java.util.ArrayList<>(sortedIds.size());
        for (Long padId : sortedIds) {
            PadInfo pad = padInfoMapper.lockById(padId);
            if (pad == null) {
                throw new RuntimeException("垫板不存在或已删除（ID：" + padId + "）");
            }
            assertPadReservable(pad);
            // 锁板口径：登记后至到期前（含待生效）不可再登记新预留，先释放或等其到期
            assertNoOpenReserve(pad.getId(),
                    "垫板【" + pad.getPadCode() + "】已存在未到期的换模预留，请先释放或待其到期后再登记");
            pads.add(pad);
        }

        // 并发兜底：极端并发下时段重叠记录由这里再次拦截（常规重复在上面按垫板直接拦截）
        List<PadMoldReserveRecord> openRecords = reserveRecordMapper.selectList(
                new LambdaQueryWrapper<PadMoldReserveRecord>()
                        .in(PadMoldReserveRecord::getStatus,
                                PadMoldReserveRecord.STATUS_PENDING,
                                PadMoldReserveRecord.STATUS_ACTIVE)
                        .lt(PadMoldReserveRecord::getStartTime, dto.getEndTime())
                        .gt(PadMoldReserveRecord::getEndTime, startTime));
        if (!openRecords.isEmpty()) {
            Set<Long> openReserveIds = openRecords.stream()
                    .map(PadMoldReserveRecord::getId).collect(Collectors.toSet());
            List<PadMoldReserveItem> overlapItems = reserveItemMapper.selectList(
                    new LambdaQueryWrapper<PadMoldReserveItem>()
                            .in(PadMoldReserveItem::getReserveId, openReserveIds)
                            .in(PadMoldReserveItem::getPadId, sortedIds));
            if (!overlapItems.isEmpty()) {
                String codes = overlapItems.stream().map(PadMoldReserveItem::getPadCode)
                        .distinct().limit(5).collect(Collectors.joining("、"));
                throw new RuntimeException("垫板【" + codes + "】在该时段已存在未释放的预留，请先释放或调整生效时段");
            }
        }

        PadMoldReserveRecord record = new PadMoldReserveRecord();
        record.setMoldCode(moldCode);
        record.setMoldName(trimToNull(dto.getMoldName()));
        record.setProductionLine(trimToNull(dto.getProductionLine()));
        record.setStartTime(startTime);
        record.setEndTime(dto.getEndTime());
        record.setOperator(operator);
        record.setStatus(startTime.isAfter(now)
                ? PadMoldReserveRecord.STATUS_PENDING
                : PadMoldReserveRecord.STATUS_ACTIVE);
        record.setRemark(trimToNull(dto.getRemark()));
        record.setCreateTime(now);
        record.setUpdateTime(now);
        reserveRecordMapper.insert(record);

        for (PadInfo pad : pads) {
            PadMoldReserveItem item = new PadMoldReserveItem();
            item.setReserveId(record.getId());
            item.setPadId(pad.getId());
            item.setPadCode(pad.getPadCode());
            item.setMoldType(pad.getMoldType());
            item.setLayerCode(pad.getShelfLayerCode());
            item.setCreateTime(now);
            reserveItemMapper.insert(item);
        }
        record.setPadCount(pads.size());
        record.setItems(reserveItemMapper.selectByReserveId(record.getId()));
        return record;
    }

    /** 预留登记时的垫板校验：必须在架、可用且未被领用离架 */
    private void assertPadReservable(PadInfo pad) {
        String status = pad.getMaintenanceStatus();
        if ("PENDING".equals(status)) {
            throw new RuntimeException("垫板【" + pad.getPadCode() + "】处于待检状态，不可预留，请先在保养台账恢复为可用");
        }
        if ("DISABLED".equals(status)) {
            throw new RuntimeException("垫板【" + pad.getPadCode() + "】已停用，不可预留，请先在保养台账恢复");
        }
        if ("SCRAPPED".equals(status)) {
            throw new RuntimeException("垫板【" + pad.getPadCode() + "】已报废出库，不可预留");
        }
        if ("BORROWED".equals(pad.getBorrowStatus())) {
            throw new RuntimeException("垫板【" + pad.getPadCode() + "】已被领用离架，归还上架后再预留");
        }
        if (pad.getShelfLayerCode() == null || pad.getShelfLayerCode().isEmpty()) {
            throw new RuntimeException("垫板【" + pad.getPadCode() + "】当前未在货架层位上，仅在架垫板可预留");
        }
    }

    /**
     * 垫板存在未到期预留（含待生效）时按给定消息拒绝。
     * 用于重复登记拦截、保养强制离架、报废出库与删除档案等场景。
     * 按结束时间实时判断，状态列未惰性刷新也不会误拦。
     */
    private void assertNoOpenReserve(Long padId, String message) {
        List<PadMoldReserveItem> items = reserveItemMapper.selectList(
                new LambdaQueryWrapper<PadMoldReserveItem>()
                        .eq(PadMoldReserveItem::getPadId, padId));
        if (items.isEmpty()) {
            return;
        }
        Set<Long> reserveIds = items.stream().map(PadMoldReserveItem::getReserveId)
                .collect(Collectors.toSet());
        Long count = reserveRecordMapper.selectCount(
                new LambdaQueryWrapper<PadMoldReserveRecord>()
                        .in(PadMoldReserveRecord::getId, reserveIds)
                        .in(PadMoldReserveRecord::getStatus,
                                PadMoldReserveRecord.STATUS_PENDING,
                                PadMoldReserveRecord.STATUS_ACTIVE)
                        .gt(PadMoldReserveRecord::getEndTime, LocalDateTime.now()));
        if (count > 0) {
            throw new RuntimeException(message);
        }
    }

    /**
     * 离架操作前校验（保养自动离架、报废出库）：垫板存在未到期预留（含待生效）时拒绝。
     * 预留登记要求垫板在架，预留单存续期间强制离架会导致预留板无层位、换模上线无板可用。
     * 按结束时间实时判断，状态列未惰性刷新也不会误拦。
     */
    public void assertNotReservedForOffShelf(Long padId, String padCode) {
        assertNoOpenReserve(padId,
                "垫板【" + padCode + "】存在未释放的换模预留，预留期内不可离架，请先释放预留或待其到期");
    }

    /** 删除档案前校验：存在未释放预留的垫板不能删除，避免预留台账留下无主明细 */
    public void assertNotReservedForDelete(Long padId, String padCode) {
        assertNoOpenReserve(padId,
                "垫板【" + padCode + "】存在未释放的换模预留，请先释放预留或待其到期后再删除档案");
    }

    /**
     * 手工释放预留：必须填写释放结论；仅“待生效/生效中”记录可释放，条件更新防止并发重复释放。
     * 释放后预留板立即恢复可领、可解绑换层。
     */
    @Transactional(rollbackFor = Exception.class)
    public PadMoldReserveRecord release(PadMoldReserveReleaseDTO dto) {
        if (dto.getId() == null) {
            throw new RuntimeException("预留记录不存在");
        }
        PadMoldReserveRecord record = reserveRecordMapper.selectById(dto.getId());
        if (record == null) {
            throw new RuntimeException("预留记录不存在");
        }
        if (!PadMoldReserveRecord.STATUS_PENDING.equals(record.getStatus())
                && !PadMoldReserveRecord.STATUS_ACTIVE.equals(record.getStatus())) {
            throw new RuntimeException("该预留记录已释放，禁止重复操作");
        }
        String conclusion = trim(dto.getReleaseConclusion());
        if (conclusion.isEmpty()) {
            throw new RuntimeException("手工释放预留必须填写释放结论");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime releaseTime = dto.getReleaseTime() != null ? dto.getReleaseTime() : now;
        if (releaseTime.isBefore(record.getStartTime())) {
            throw new RuntimeException("释放时间不能早于预留开始时间");
        }
        String releaseOperator = trim(dto.getReleaseOperator());
        if (releaseOperator.isEmpty()) {
            releaseOperator = record.getOperator();
        }

        // 条件更新：仅待生效/生效中记录可被释放，并发下重复释放直接失败
        LambdaUpdateWrapper<PadMoldReserveRecord> update = new LambdaUpdateWrapper<>();
        update.eq(PadMoldReserveRecord::getId, record.getId())
                .in(PadMoldReserveRecord::getStatus,
                        PadMoldReserveRecord.STATUS_PENDING,
                        PadMoldReserveRecord.STATUS_ACTIVE)
                .set(PadMoldReserveRecord::getStatus, PadMoldReserveRecord.STATUS_RELEASED)
                .set(PadMoldReserveRecord::getReleaseTime, releaseTime)
                .set(PadMoldReserveRecord::getReleaseConclusion, conclusion)
                .set(PadMoldReserveRecord::getReleaseOperator, releaseOperator)
                .set(PadMoldReserveRecord::getUpdateTime, now);
        int rows = reserveRecordMapper.update(null, update);
        if (rows == 0) {
            throw new RuntimeException("该预留记录已释放，禁止重复操作");
        }
        return getById(record.getId());
    }

    /**
     * 领用/解绑/换层前校验：垫板存在已登记且未到期的预留（含待生效）时拒绝。
     * 待生效预留同样锁板：预留是对换模上线的承诺，生效前被领用离架会导致上线无板。
     * 约束按结束时间实时判断（不依赖状态列是否已惰性刷新），到期自动放行，与领用可选板口径一致。
     * 调用方需在事务内先对垫板行加锁（lockById），与预留登记串行化。
     */
    public void assertNotEffectivelyReserved(PadInfo pad) {
        if (pad == null) {
            return;
        }
        PadMoldReserveRecord reserve = findEffectiveReserve(pad.getId(), LocalDateTime.now());
        if (reserve != null) {
            throw new RuntimeException("垫板【" + pad.getPadCode() + "】已预留给模具【"
                    + reserve.getMoldCode() + "】，预留期内禁止领用/解绑/换层；请先在换模预留台账释放或等待到期");
        }
    }

    /** 查询垫板在指定时刻仍未到期的预留单（PENDING/ACTIVE 且结束时间晚于当前时刻），无则 null */
    private PadMoldReserveRecord findEffectiveReserve(Long padId, LocalDateTime now) {
        List<PadMoldReserveItem> items = reserveItemMapper.selectList(
                new LambdaQueryWrapper<PadMoldReserveItem>()
                        .eq(PadMoldReserveItem::getPadId, padId));
        if (items.isEmpty()) {
            return null;
        }
        Set<Long> reserveIds = items.stream().map(PadMoldReserveItem::getReserveId)
                .collect(Collectors.toCollection(HashSet::new));
        return reserveRecordMapper.selectOne(
                new LambdaQueryWrapper<PadMoldReserveRecord>()
                        .in(PadMoldReserveRecord::getId, reserveIds)
                        .in(PadMoldReserveRecord::getStatus,
                                PadMoldReserveRecord.STATUS_PENDING,
                                PadMoldReserveRecord.STATUS_ACTIVE)
                        .gt(PadMoldReserveRecord::getEndTime, now)
                        .orderByDesc(PadMoldReserveRecord::getStartTime)
                        .orderByDesc(PadMoldReserveRecord::getId)
                        .last("LIMIT 1"));
    }

    public Map<String, Object> statistics() {
        refreshStatus();
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeCount", reserveRecordMapper.selectCount(
                new LambdaQueryWrapper<PadMoldReserveRecord>()
                        .eq(PadMoldReserveRecord::getStatus, PadMoldReserveRecord.STATUS_ACTIVE)));
        stats.put("pendingCount", reserveRecordMapper.selectCount(
                new LambdaQueryWrapper<PadMoldReserveRecord>()
                        .eq(PadMoldReserveRecord::getStatus, PadMoldReserveRecord.STATUS_PENDING)));
        stats.put("expiredCount", reserveRecordMapper.selectCount(
                new LambdaQueryWrapper<PadMoldReserveRecord>()
                        .eq(PadMoldReserveRecord::getStatus, PadMoldReserveRecord.STATUS_EXPIRED)));
        stats.put("releasedCount", reserveRecordMapper.selectCount(
                new LambdaQueryWrapper<PadMoldReserveRecord>()
                        .eq(PadMoldReserveRecord::getStatus, PadMoldReserveRecord.STATUS_RELEASED)));
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        stats.put("monthReserveCount", reserveRecordMapper.selectCount(
                new LambdaQueryWrapper<PadMoldReserveRecord>()
                        .ge(PadMoldReserveRecord::getStartTime, monthStart)));
        return stats;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
