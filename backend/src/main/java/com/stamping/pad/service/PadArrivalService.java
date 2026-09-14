package com.stamping.pad.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.dto.PadArrivalDTO;
import com.stamping.pad.dto.PadArrivalItemDTO;
import com.stamping.pad.dto.PadArrivalPassDTO;
import com.stamping.pad.dto.PadArrivalQueryDTO;
import com.stamping.pad.dto.PadArrivalRejectDTO;
import com.stamping.pad.entity.LayerAdjustRecord;
import com.stamping.pad.entity.PadArrivalBatch;
import com.stamping.pad.entity.PadArrivalItem;
import com.stamping.pad.entity.PadInfo;
import com.stamping.pad.mapper.LayerAdjustRecordMapper;
import com.stamping.pad.mapper.PadArrivalBatchMapper;
import com.stamping.pad.mapper.PadArrivalItemMapper;
import com.stamping.pad.mapper.PadInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 到货待检（待检层）：新到垫板先落待检层，登记到货批次、到货时间与经办人。
 * 待检层垫板（stock_status=QUARANTINE）不可领用、换绑或计入可用库存；
 * 质检通过整批转正式层（逐块指定目标层位并写 BIND 调整记录），
 * 判退整批离库（stock_status=REJECTED，档案冻结留存）。
 */
@Service
@RequiredArgsConstructor
public class PadArrivalService {

    /** 待检层：到货已登记未判定 */
    public static final String STOCK_QUARANTINE = "QUARANTINE";
    /** 正式在库：质检通过或常规建档/导入 */
    public static final String STOCK_OFFICIAL = "OFFICIAL";
    /** 判退离库：档案冻结，不再参与任何库存操作 */
    public static final String STOCK_REJECTED = "REJECTED";

    private final PadArrivalBatchMapper batchMapper;
    private final PadArrivalItemMapper itemMapper;
    private final PadInfoMapper padInfoMapper;
    private final LayerAdjustRecordMapper recordMapper;
    private final ShelfLayerService shelfLayerService;

    public Page<PadArrivalBatch> pageList(PadArrivalQueryDTO query) {
        Page<PadArrivalBatch> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<PadArrivalBatch> result = batchMapper.selectPageList(page, query);
        fillItems(result.getRecords());
        return result;
    }

    public PadArrivalBatch getById(Long id) {
        PadArrivalBatch batch = batchMapper.selectDetailById(id);
        if (batch != null) {
            batch.setItems(itemMapper.selectByBatchId(id));
        }
        return batch;
    }

    /** 批量回填批内垫板清单（含垫板当前库存状态/层位，供台账回看对照） */
    private void fillItems(List<PadArrivalBatch> batches) {
        if (batches == null || batches.isEmpty()) {
            return;
        }
        List<Long> ids = batches.stream().map(PadArrivalBatch::getId).toList();
        Map<Long, List<PadArrivalItem>> itemMap = itemMapper.selectByBatchIds(ids).stream()
                .collect(Collectors.groupingBy(PadArrivalItem::getBatchId));
        batches.forEach(b -> b.setItems(itemMap.getOrDefault(b.getId(), List.of())));
    }

    /**
     * 登记到货：新到垫板先落待检层。
     * 批内每块垫板创建档案（stock_status=QUARANTINE、无层位），
     * 质检判定前不可领用/换绑/保养/报废/预留/删除，也不计入可用库存。
     */
    @Transactional(rollbackFor = Exception.class)
    public PadArrivalBatch register(PadArrivalDTO dto) {
        String operator = trim(dto.getOperator());
        if (operator.isEmpty()) {
            throw new RuntimeException("经办人不能为空");
        }
        List<PadArrivalItemDTO> rows = dto.getItems() == null ? List.of() : dto.getItems();
        if (rows.isEmpty()) {
            throw new RuntimeException("请至少登记一块到货垫板");
        }

        // 逐行校验编号：非空、长度合法、批次内不重复
        Set<String> seen = new HashSet<>();
        List<String> codes = new ArrayList<>(rows.size());
        for (PadArrivalItemDTO row : rows) {
            String code = trim(row.getPadCode());
            if (code.isEmpty()) {
                throw new RuntimeException("到货明细中存在空垫板编号，请补全后再提交");
            }
            if (code.length() > 64) {
                throw new RuntimeException("垫板编号【" + code + "】长度超过 64 位");
            }
            if (!seen.add(code)) {
                throw new RuntimeException("垫板编号【" + code + "】在本批次内重复");
            }
            assertDimensionNonNegative(row.getLength(), "长度", code);
            assertDimensionNonNegative(row.getWidth(), "宽度", code);
            assertDimensionNonNegative(row.getThickness(), "厚度", code);
            codes.add(code);
        }
        // 与档案库现有编号冲突校验（uk_pad_code 兜底，这里提前给出友好提示）
        Long existCount = padInfoMapper.selectCount(
                new LambdaQueryWrapper<PadInfo>().in(PadInfo::getPadCode, codes));
        if (existCount > 0) {
            List<String> existed = padInfoMapper.selectList(
                    new LambdaQueryWrapper<PadInfo>()
                            .select(PadInfo::getPadCode)
                            .in(PadInfo::getPadCode, codes))
                    .stream().map(PadInfo::getPadCode).limit(5).toList();
            throw new RuntimeException("垫板编号【" + String.join("、", existed) + "】已存在档案，请勿重复到货登记");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime arrivalTime = dto.getArrivalTime() != null ? dto.getArrivalTime() : now;

        PadArrivalBatch batch = new PadArrivalBatch();
        batch.setBatchNo(generateBatchNo());
        batch.setSupplier(trimToNull(dto.getSupplier()));
        batch.setArrivalTime(arrivalTime);
        batch.setOperator(operator);
        batch.setPadCount(rows.size());
        batch.setStatus(PadArrivalBatch.STATUS_PENDING);
        batch.setRemark(trimToNull(dto.getRemark()));
        batch.setCreateTime(now);
        batch.setUpdateTime(now);
        batchMapper.insert(batch);

        for (PadArrivalItemDTO row : rows) {
            PadInfo pad = new PadInfo();
            pad.setPadCode(trim(row.getPadCode()));
            pad.setMoldType(trimToNull(row.getMoldType()));
            pad.setLength(row.getLength());
            pad.setWidth(row.getWidth());
            pad.setThickness(row.getThickness());
            pad.setMaintenanceStatus("AVAILABLE");
            // 新到垫板落待检层：无层位、不计可用库存，待质检判定去向
            pad.setStockStatus(STOCK_QUARANTINE);
            pad.setRemark(trimToNull(row.getRemark()));
            pad.setCreateTime(now);
            pad.setUpdateTime(now);
            padInfoMapper.insert(pad);

            PadArrivalItem item = new PadArrivalItem();
            item.setBatchId(batch.getId());
            item.setPadId(pad.getId());
            item.setPadCode(pad.getPadCode());
            item.setMoldType(pad.getMoldType());
            item.setCreateTime(now);
            itemMapper.insert(item);
        }
        return getById(batch.getId());
    }

    /**
     * 质检通过：整批转正式层。
     * 批内每块垫板须指定目标层位；层位校验（存在/未封锁/未超实际配额）与绑定在同一事务内
     * 逐块进行（lockAndAssertCapacity 行锁层位，同层多块时占用数随事务内写入实时增长），
     * 并写入 BIND 调整记录；批次状态条件更新，防止并发重复判定。
     */
    @Transactional(rollbackFor = Exception.class)
    public PadArrivalBatch passInspection(PadArrivalPassDTO dto) {
        if (dto.getId() == null) {
            throw new RuntimeException("到货批次不存在");
        }
        PadArrivalBatch batch = batchMapper.lockById(dto.getId());
        if (batch == null) {
            throw new RuntimeException("到货批次不存在");
        }
        if (!PadArrivalBatch.STATUS_PENDING.equals(batch.getStatus())) {
            throw new RuntimeException("该批次已完成质检判定，禁止重复操作");
        }
        String conclusion = trim(dto.getInspectConclusion());
        if (conclusion.isEmpty()) {
            throw new RuntimeException("质检通过必须填写质检结论");
        }

        List<PadArrivalItem> items = itemMapper.selectByBatchId(batch.getId());
        if (items.isEmpty()) {
            throw new RuntimeException("批次明细缺失，无法判定，请联系管理员核查数据");
        }
        // 目标层位逐块齐全校验：转正式层必须整批同时上架，不允许漏块
        Map<Long, String> targetMap = new HashMap<>();
        if (dto.getTargets() != null) {
            for (PadArrivalPassDTO.Target target : dto.getTargets()) {
                if (target.getPadId() != null) {
                    targetMap.put(target.getPadId(),
                            target.getLayerCode() == null ? "" : target.getLayerCode().trim());
                }
            }
        }
        for (PadArrivalItem item : items) {
            String layerCode = targetMap.get(item.getPadId());
            if (layerCode == null || layerCode.isEmpty()) {
                throw new RuntimeException("垫板【" + item.getPadCode() + "】未指定转正式层位，整批通过须逐块上架");
            }
        }
        // 目标必须都属于本批次，防止把其他批次/档案的垫板误入正式层
        Set<Long> batchPadIds = items.stream().map(PadArrivalItem::getPadId).collect(Collectors.toSet());
        for (Long padId : targetMap.keySet()) {
            if (!batchPadIds.contains(padId)) {
                throw new RuntimeException("转正式层目标包含非本批次垫板（ID：" + padId + "），请刷新后重试");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime inspectTime = dto.getInspectTime() != null ? dto.getInspectTime() : now;
        String inspector = trim(dto.getInspector());
        if (inspector.isEmpty()) {
            inspector = batch.getOperator();
        }

        // 按垫板ID升序逐块行锁：与领用/预留等垫板行锁口径一致，避免死锁
        List<PadArrivalItem> sorted = items.stream()
                .sorted(java.util.Comparator.comparing(PadArrivalItem::getPadId)).toList();
        for (PadArrivalItem item : sorted) {
            PadInfo pad = padInfoMapper.lockById(item.getPadId());
            if (pad == null) {
                throw new RuntimeException("垫板【" + item.getPadCode() + "】档案不存在或已删除");
            }
            if (!STOCK_QUARANTINE.equals(pad.getStockStatus())) {
                throw new RuntimeException("垫板【" + item.getPadCode() + "】库存状态已变化（非待检层），请刷新后重试");
            }
            String layerCode = targetMap.get(item.getPadId());
            // 目标层位须存在、未封锁且占用未达实际配额（同事务内逐块计数，同层多块不会超配额）
            shelfLayerService.lockAndAssertCapacity(layerCode);

            LambdaUpdateWrapper<PadInfo> padUpdate = new LambdaUpdateWrapper<>();
            padUpdate.eq(PadInfo::getId, pad.getId())
                    .set(PadInfo::getStockStatus, STOCK_OFFICIAL)
                    .set(PadInfo::getShelfLayerCode, layerCode)
                    .set(PadInfo::getBindTime, inspectTime)
                    .set(PadInfo::getUpdateTime, now);
            padInfoMapper.update(null, padUpdate);

            LayerAdjustRecord record = new LayerAdjustRecord();
            record.setPadId(pad.getId());
            record.setPadCode(pad.getPadCode());
            record.setOldLayerCode(null);
            record.setNewLayerCode(layerCode);
            record.setAdjustType("BIND");
            record.setOperator(inspector);
            record.setAdjustReason("到货批次 " + batch.getBatchNo() + " 质检通过转正式层：" + conclusion);
            record.setAdjustTime(inspectTime);
            recordMapper.insert(record);

            LambdaUpdateWrapper<PadArrivalItem> itemUpdate = new LambdaUpdateWrapper<>();
            itemUpdate.eq(PadArrivalItem::getId, item.getId())
                    .set(PadArrivalItem::getTargetLayerCode, layerCode);
            itemMapper.update(null, itemUpdate);
        }

        // 条件更新：仅待检中批次可判定，并发下重复判定直接失败
        LambdaUpdateWrapper<PadArrivalBatch> batchUpdate = new LambdaUpdateWrapper<>();
        batchUpdate.eq(PadArrivalBatch::getId, batch.getId())
                .eq(PadArrivalBatch::getStatus, PadArrivalBatch.STATUS_PENDING)
                .set(PadArrivalBatch::getStatus, PadArrivalBatch.STATUS_PASSED)
                .set(PadArrivalBatch::getInspectTime, inspectTime)
                .set(PadArrivalBatch::getInspector, inspector)
                .set(PadArrivalBatch::getInspectConclusion, conclusion)
                .set(PadArrivalBatch::getUpdateTime, now);
        int rows = batchMapper.update(null, batchUpdate);
        if (rows == 0) {
            throw new RuntimeException("该批次已完成质检判定，禁止重复操作");
        }
        return getById(batch.getId());
    }

    /**
     * 判退离库：整批判定离库。
     * 批内垫板 stock_status 置为 REJECTED（档案冻结留存，不再参与领用/上架/保养等任何库存操作）；
     * 批次状态条件更新，防止并发重复判定。
     */
    @Transactional(rollbackFor = Exception.class)
    public PadArrivalBatch reject(PadArrivalRejectDTO dto) {
        if (dto.getId() == null) {
            throw new RuntimeException("到货批次不存在");
        }
        PadArrivalBatch batch = batchMapper.lockById(dto.getId());
        if (batch == null) {
            throw new RuntimeException("到货批次不存在");
        }
        if (!PadArrivalBatch.STATUS_PENDING.equals(batch.getStatus())) {
            throw new RuntimeException("该批次已完成质检判定，禁止重复操作");
        }
        String conclusion = trim(dto.getInspectConclusion());
        if (conclusion.isEmpty()) {
            throw new RuntimeException("判退离库必须填写判退结论/原因");
        }

        List<PadArrivalItem> items = itemMapper.selectByBatchId(batch.getId());
        if (items.isEmpty()) {
            throw new RuntimeException("批次明细缺失，无法判定，请联系管理员核查数据");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime inspectTime = dto.getInspectTime() != null ? dto.getInspectTime() : now;
        String inspector = trim(dto.getInspector());
        if (inspector.isEmpty()) {
            inspector = batch.getOperator();
        }

        List<PadArrivalItem> sorted = items.stream()
                .sorted(java.util.Comparator.comparing(PadArrivalItem::getPadId)).toList();
        for (PadArrivalItem item : sorted) {
            PadInfo pad = padInfoMapper.lockById(item.getPadId());
            if (pad == null) {
                throw new RuntimeException("垫板【" + item.getPadCode() + "】档案不存在或已删除");
            }
            if (!STOCK_QUARANTINE.equals(pad.getStockStatus())) {
                throw new RuntimeException("垫板【" + item.getPadCode() + "】库存状态已变化（非待检层），请刷新后重试");
            }
            // 防御性离架：待检层垫板正常无层位，若异常占层则随判退释放，不留无主占用
            if (pad.getShelfLayerCode() != null && !pad.getShelfLayerCode().isEmpty()) {
                LayerAdjustRecord record = new LayerAdjustRecord();
                record.setPadId(pad.getId());
                record.setPadCode(pad.getPadCode());
                record.setOldLayerCode(pad.getShelfLayerCode());
                record.setNewLayerCode(null);
                record.setAdjustType("UNBIND");
                record.setOperator(inspector);
                record.setAdjustReason("到货批次 " + batch.getBatchNo() + " 判退离库，自动离架");
                record.setAdjustTime(inspectTime);
                recordMapper.insert(record);
            }
            LambdaUpdateWrapper<PadInfo> padUpdate = new LambdaUpdateWrapper<>();
            padUpdate.eq(PadInfo::getId, pad.getId())
                    .set(PadInfo::getStockStatus, STOCK_REJECTED)
                    .set(PadInfo::getShelfLayerCode, null)
                    .set(PadInfo::getBindTime, null)
                    .set(PadInfo::getUpdateTime, now);
            padInfoMapper.update(null, padUpdate);
        }

        LambdaUpdateWrapper<PadArrivalBatch> batchUpdate = new LambdaUpdateWrapper<>();
        batchUpdate.eq(PadArrivalBatch::getId, batch.getId())
                .eq(PadArrivalBatch::getStatus, PadArrivalBatch.STATUS_PENDING)
                .set(PadArrivalBatch::getStatus, PadArrivalBatch.STATUS_REJECTED)
                .set(PadArrivalBatch::getInspectTime, inspectTime)
                .set(PadArrivalBatch::getInspector, inspector)
                .set(PadArrivalBatch::getInspectConclusion, conclusion)
                .set(PadArrivalBatch::getRemark,
                        trimToNull(dto.getRemark()) != null ? trimToNull(dto.getRemark()) : batch.getRemark())
                .set(PadArrivalBatch::getUpdateTime, now);
        int rows = batchMapper.update(null, batchUpdate);
        if (rows == 0) {
            throw new RuntimeException("该批次已完成质检判定，禁止重复操作");
        }
        return getById(batch.getId());
    }

    public Map<String, Object> statistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("pendingCount", batchMapper.selectCount(
                new LambdaQueryWrapper<PadArrivalBatch>()
                        .eq(PadArrivalBatch::getStatus, PadArrivalBatch.STATUS_PENDING)));
        stats.put("passedCount", batchMapper.selectCount(
                new LambdaQueryWrapper<PadArrivalBatch>()
                        .eq(PadArrivalBatch::getStatus, PadArrivalBatch.STATUS_PASSED)));
        stats.put("rejectedCount", batchMapper.selectCount(
                new LambdaQueryWrapper<PadArrivalBatch>()
                        .eq(PadArrivalBatch::getStatus, PadArrivalBatch.STATUS_REJECTED)));
        // 待检占用：待检层垫板块数，数据概览单独标出，不计入可用库存
        stats.put("pendingPadCount", padInfoMapper.selectCount(
                new LambdaQueryWrapper<PadInfo>().eq(PadInfo::getStockStatus, STOCK_QUARANTINE)));
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        stats.put("monthArrivalCount", batchMapper.selectCount(
                new LambdaQueryWrapper<PadArrivalBatch>()
                        .ge(PadArrivalBatch::getArrivalTime, monthStart)));
        return stats;
    }

    /** 到货批次号：DH + 日期时间 + 随机串，唯一约束兜底重试 */
    private String generateBatchNo() {
        String batchNo;
        do {
            batchNo = "DH" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
        } while (batchMapper.selectCount(
                new LambdaQueryWrapper<PadArrivalBatch>()
                        .eq(PadArrivalBatch::getBatchNo, batchNo)) > 0);
        return batchNo;
    }

    private void assertDimensionNonNegative(BigDecimal value, String label, String padCode) {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("垫板【" + padCode + "】" + label + "不能为负数");
        }
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
