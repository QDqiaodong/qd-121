package com.stamping.pad.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.dto.InventoryExtraItemDTO;
import com.stamping.pad.dto.InventoryItemMarkDTO;
import com.stamping.pad.dto.InventorySheetCloseDTO;
import com.stamping.pad.dto.InventorySheetCreateDTO;
import com.stamping.pad.dto.InventorySheetQueryDTO;
import com.stamping.pad.dto.InventorySheetSubmitDTO;
import com.stamping.pad.entity.PadInfo;
import com.stamping.pad.entity.PadInventoryItem;
import com.stamping.pad.entity.PadInventorySheet;
import com.stamping.pad.entity.ShelfLayer;
import com.stamping.pad.mapper.PadInfoMapper;
import com.stamping.pad.mapper.PadInventoryItemMapper;
import com.stamping.pad.mapper.PadInventorySheetMapper;
import com.stamping.pad.mapper.ShelfLayerMapper;
import com.stamping.pad.vo.PadInventoryDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 交班盘点：交班时库房按货架层清点在架垫板，登记班次、盘点人。
 * 开单快照账目在架清单，逐块标记相符/缺失，可补录现场多出；提交时存在差异必须登记差异原因，
 * 单据进入“待闭环”（未平账）：覆盖层位在概览/层位页标出，未闭环前禁止归还上架；
 * 差异处理完毕后填写闭环结论闭环，层位自动恢复可归还。账实相符的单据提交即自动闭环。
 */
@Service
@RequiredArgsConstructor
public class PadInventoryService {

    private static final Set<String> SCOPE_SET = Set.of("LAYER", "SHELF");
    private static final Set<String> SHIFT_SET = Set.of("DAY", "MIDDLE", "NIGHT");
    private static final Set<String> MARK_RESULT_SET = Set.of("MATCH", "MISSING");

    private final PadInventorySheetMapper sheetMapper;
    private final PadInventoryItemMapper itemMapper;
    private final ShelfLayerMapper shelfLayerMapper;
    private final PadInfoMapper padInfoMapper;
    private final ShelfLayerService shelfLayerService;

    public Page<PadInventorySheet> pageList(InventorySheetQueryDTO query) {
        Page<PadInventorySheet> page = new Page<>(query.getPageNum(), query.getPageSize());
        return sheetMapper.selectPageList(page, query);
    }

    /** 盘点单详情：单据 + 覆盖层位实时占用（与层位页/概览同口径）+ 明细列表 */
    public PadInventoryDetailVO getDetail(Long id) {
        PadInventorySheet sheet = sheetMapper.selectById(id);
        if (sheet == null) {
            throw new RuntimeException("盘点单不存在");
        }
        List<PadInventoryItem> items = itemMapper.selectDetailBySheetId(id);
        fillSheetCounts(sheet, items);

        Set<String> covered = new HashSet<>(sheet.getCoveredLayerList());
        List<Map<String, Object>> layers = new ArrayList<>();
        for (ShelfLayer layer : shelfLayerService.listAll()) {
            if (!covered.contains(layer.getLayerCode())) {
                continue;
            }
            Map<String, Object> info = new HashMap<>();
            info.put("layerCode", layer.getLayerCode());
            info.put("layerName", layer.getLayerName());
            info.put("padCount", layer.getPadCount() == null ? 0 : layer.getPadCount());
            info.put("capacity", layer.getCapacity() == null ? 0 : layer.getCapacity());
            info.put("effectiveCapacity",
                    layer.getEffectiveCapacity() == null ? info.get("capacity") : layer.getEffectiveCapacity());
            info.put("blocked", layer.getActiveBlock() != null);
            info.put("unbalanced", layer.getActiveUnbalanced() != null);
            layers.add(info);
        }

        PadInventoryDetailVO vo = new PadInventoryDetailVO();
        vo.setSheet(sheet);
        vo.setLayers(layers);
        vo.setItems(items);
        return vo;
    }

    /** 聚合明细统计回填到单据（相符/缺失/多出/已标记），列表与详情同口径 */
    private void fillSheetCounts(PadInventorySheet sheet, List<PadInventoryItem> items) {
        int match = 0, missing = 0, extra = 0, marked = 0;
        for (PadInventoryItem item : items) {
            if ("EXTRA".equals(item.getItemType())) {
                extra++;
                continue;
            }
            if ("MATCH".equals(item.getCheckResult())) {
                match++;
            } else if ("MISSING".equals(item.getCheckResult())) {
                missing++;
            }
            if (item.getCheckResult() != null) {
                marked++;
            }
        }
        sheet.setTotalCount(items.size());
        sheet.setMatchCount(match);
        sheet.setMissingCount(missing);
        sheet.setExtraCount(extra);
        sheet.setMarkedCount(marked);
        sheet.setLayerCount(sheet.getCoveredLayerList().size());
    }

    /**
     * 开单：按层位/货架盘点，登记班次与盘点人；开单时快照覆盖层位的在架垫板为账目明细。
     * 同一层位同一时间仅允许一张“盘点中”单据（事务内行锁覆盖层位，防止并发开单交错）。
     */
    @Transactional(rollbackFor = Exception.class)
    public PadInventorySheet create(InventorySheetCreateDTO dto) {
        String scopeType = trim(dto.getScopeType());
        if (!SCOPE_SET.contains(scopeType)) {
            throw new RuntimeException("盘点范围非法，仅支持：按层位、按货架");
        }
        String shift = trim(dto.getShift());
        if (!SHIFT_SET.contains(shift)) {
            throw new RuntimeException("班次非法，仅支持：白班、中班、夜班");
        }
        String inspector = trim(dto.getInspector());
        if (inspector.isEmpty()) {
            throw new RuntimeException("盘点人不能为空");
        }

        // 计算覆盖层位：按层位取目标层，按货架取该货架全部层位
        String shelfCode = trimToNull(dto.getShelfCode());
        String layerCode = trimToNull(dto.getLayerCode());
        List<ShelfLayer> covered;
        if ("LAYER".equals(scopeType)) {
            if (layerCode == null) {
                throw new RuntimeException("请选择要盘点的层位");
            }
            ShelfLayer layer = shelfLayerMapper.selectByLayerCode(layerCode);
            if (layer == null) {
                throw new RuntimeException("货架分层不存在");
            }
            covered = List.of(layer);
            shelfCode = layer.getShelfCode();
            layerCode = layer.getLayerCode();
        } else {
            if (shelfCode == null) {
                throw new RuntimeException("请选择要盘点的货架");
            }
            covered = shelfLayerMapper.selectList(new LambdaQueryWrapper<ShelfLayer>()
                    .eq(ShelfLayer::getShelfCode, shelfCode)
                    .orderByAsc(ShelfLayer::getLayerOrder)
                    .orderByAsc(ShelfLayer::getLayerCode));
            if (covered.isEmpty()) {
                throw new RuntimeException("货架【" + shelfCode + "】下没有层位，无法盘点");
            }
            layerCode = null;
        }

        // 按层位编码升序逐层行锁（与上架占用同一把层位行锁），串行化并发开单，避免死锁
        List<String> coveredCodes = covered.stream().map(ShelfLayer::getLayerCode).sorted().toList();
        for (String code : coveredCodes) {
            shelfLayerMapper.lockByLayerCode(code);
        }
        // 同一层位仅允许一张盘点中单据：已闭环/待闭环单据不拦截复盘
        for (PadInventorySheet open : sheetMapper.selectInProgress()) {
            Set<String> openLayers = new HashSet<>(open.getCoveredLayerList());
            for (String code : coveredCodes) {
                if (openLayers.contains(code)) {
                    throw new RuntimeException("层位【" + code + "】已存在盘点中的单据（单号 "
                            + open.getSheetNo() + "），请先提交或取消该单据");
                }
            }
        }

        LocalDateTime now = LocalDateTime.now();
        PadInventorySheet sheet = new PadInventorySheet();
        sheet.setSheetNo(generateSheetNo());
        sheet.setScopeType(scopeType);
        sheet.setShelfCode(shelfCode);
        sheet.setLayerCode(layerCode);
        sheet.setCoveredLayers("," + String.join(",", coveredCodes) + ",");
        sheet.setShift(shift);
        sheet.setInspector(inspector);
        sheet.setStatus(PadInventorySheet.STATUS_IN_PROGRESS);
        sheet.setStartTime(now);
        sheet.setRemark(trimToNull(dto.getRemark()));
        sheet.setCreateTime(now);
        sheet.setUpdateTime(now);
        sheetMapper.insert(sheet);

        // 快照覆盖层位的在架垫板为账目明细（以 pad_info.shelf_layer_code 实时清单为准）
        List<PadInfo> onShelfPads = padInfoMapper.selectList(new LambdaQueryWrapper<PadInfo>()
                .in(PadInfo::getShelfLayerCode, coveredCodes)
                .orderByAsc(PadInfo::getShelfLayerCode)
                .orderByAsc(PadInfo::getPadCode));
        for (PadInfo pad : onShelfPads) {
            PadInventoryItem item = new PadInventoryItem();
            item.setSheetId(sheet.getId());
            item.setItemType("LEDGER");
            item.setPadId(pad.getId());
            item.setPadCode(pad.getPadCode());
            item.setMoldType(pad.getMoldType());
            item.setLayerCode(pad.getShelfLayerCode());
            item.setCreateTime(now);
            item.setUpdateTime(now);
            itemMapper.insert(item);
        }
        return sheetMapper.selectById(sheet.getId());
    }

    /** 逐块标记账目明细：MATCH-账实相符、MISSING-缺失；仅盘点中单据可标记 */
    @Transactional(rollbackFor = Exception.class)
    public PadInventoryItem markItem(InventoryItemMarkDTO dto) {
        PadInventoryItem item = itemMapper.selectById(dto.getItemId());
        if (item == null) {
            throw new RuntimeException("盘点明细不存在");
        }
        assertInProgress(item.getSheetId());
        if (!"LEDGER".equals(item.getItemType())) {
            throw new RuntimeException("多出明细固定为“多出”，无需逐块标记");
        }
        String result = trim(dto.getCheckResult());
        if (!MARK_RESULT_SET.contains(result)) {
            throw new RuntimeException("盘点结果非法，仅支持：账实相符、缺失");
        }
        item.setCheckResult(result);
        item.setRemark(trimToNull(dto.getRemark()));
        item.setUpdateTime(LocalDateTime.now());
        itemMapper.updateById(item);
        return itemMapper.selectById(item.getId());
    }

    /** 补录现场多出垫板：所在层位必须属于本单覆盖范围，同单同层同编号不可重复补录 */
    @Transactional(rollbackFor = Exception.class)
    public PadInventoryItem addExtraItem(Long sheetId, InventoryExtraItemDTO dto) {
        PadInventorySheet sheet = assertInProgress(sheetId);
        String layerCode = trim(dto.getLayerCode());
        if (!sheet.getCoveredLayerList().contains(layerCode)) {
            throw new RuntimeException("多出垫板所在层位【" + layerCode + "】不在本单覆盖范围内");
        }
        String padCode = trim(dto.getPadCode());
        if (padCode.isEmpty()) {
            throw new RuntimeException("垫板编号不能为空");
        }
        // 已在账目清单中的编号不属于多出，应直接标记账实结果
        Long ledgerCount = itemMapper.selectCount(new LambdaQueryWrapper<PadInventoryItem>()
                .eq(PadInventoryItem::getSheetId, sheetId)
                .eq(PadInventoryItem::getItemType, "LEDGER")
                .eq(PadInventoryItem::getPadCode, padCode));
        if (ledgerCount > 0) {
            throw new RuntimeException("垫板【" + padCode + "】已在账目清单中，请直接标记盘点结果，无需补录");
        }
        if (itemMapper.selectExtraDuplicate(sheetId, layerCode, padCode) != null) {
            throw new RuntimeException("垫板【" + padCode + "】已在该层补录过，请勿重复登记");
        }

        // 编号能匹配到档案时回填垫板ID与适配模具，便于详情对照实时层位/状态
        PadInfo pad = padInfoMapper.selectOne(new LambdaQueryWrapper<PadInfo>()
                .eq(PadInfo::getPadCode, padCode).last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        PadInventoryItem item = new PadInventoryItem();
        item.setSheetId(sheetId);
        item.setItemType("EXTRA");
        item.setPadId(pad == null ? null : pad.getId());
        item.setPadCode(padCode);
        String moldType = trimToNull(dto.getMoldType());
        item.setMoldType(moldType != null ? moldType : (pad == null ? null : pad.getMoldType()));
        item.setLayerCode(layerCode);
        item.setCheckResult("EXTRA");
        item.setRemark(trimToNull(dto.getRemark()));
        item.setCreateTime(now);
        item.setUpdateTime(now);
        itemMapper.insert(item);
        return itemMapper.selectById(item.getId());
    }

    /** 删除补录的多出明细（补录错误时撤回）；仅盘点中单据的多出明细可删 */
    @Transactional(rollbackFor = Exception.class)
    public void removeExtraItem(Long itemId) {
        PadInventoryItem item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new RuntimeException("盘点明细不存在");
        }
        assertInProgress(item.getSheetId());
        if (!"EXTRA".equals(item.getItemType())) {
            throw new RuntimeException("仅补录的多出明细可删除");
        }
        itemMapper.deleteById(itemId);
    }

    /**
     * 提交盘点：全部账目明细标记完毕才能提交。
     * 存在差异（缺失/多出）时必须登记差异原因，单据进入“待闭环”（未平账），
     * 覆盖层位禁止归还上架；账实相符则提交即闭环，由系统补写闭环信息。
     */
    @Transactional(rollbackFor = Exception.class)
    public PadInventorySheet submit(InventorySheetSubmitDTO dto) {
        if (dto.getSheetId() == null) {
            throw new RuntimeException("盘点单不存在");
        }
        PadInventorySheet sheet = sheetMapper.selectById(dto.getSheetId());
        if (sheet == null) {
            throw new RuntimeException("盘点单不存在");
        }
        if (!PadInventorySheet.STATUS_IN_PROGRESS.equals(sheet.getStatus())) {
            throw new RuntimeException("该盘点单已提交，禁止重复提交");
        }
        List<PadInventoryItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PadInventoryItem>().eq(PadInventoryItem::getSheetId, sheet.getId()));
        long unmarked = items.stream()
                .filter(i -> "LEDGER".equals(i.getItemType()) && i.getCheckResult() == null)
                .count();
        if (unmarked > 0) {
            throw new RuntimeException("还有 " + unmarked + " 块账目垫板未标记盘点结果，全部标记后才能提交");
        }
        long missing = items.stream().filter(i -> "MISSING".equals(i.getCheckResult())).count();
        long extra = items.stream().filter(i -> "EXTRA".equals(i.getItemType())).count();

        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<PadInventorySheet> update = new LambdaUpdateWrapper<>();
        update.eq(PadInventorySheet::getId, sheet.getId())
                .eq(PadInventorySheet::getStatus, PadInventorySheet.STATUS_IN_PROGRESS)
                .set(PadInventorySheet::getSubmitTime, now)
                .set(PadInventorySheet::getUpdateTime, now);
        if (missing + extra > 0) {
            String diffReason = trim(dto.getDiffReason());
            if (diffReason.isEmpty()) {
                throw new RuntimeException("存在差异（缺失 " + missing + " 块、多出 " + extra
                        + " 块），提交时必须登记差异原因");
            }
            update.set(PadInventorySheet::getStatus, PadInventorySheet.STATUS_SUBMITTED)
                    .set(PadInventorySheet::getDiffReason, diffReason);
        } else {
            // 账实相符：提交即闭环，不产生未平账层位
            update.set(PadInventorySheet::getStatus, PadInventorySheet.STATUS_CLOSED)
                    .set(PadInventorySheet::getCloseTime, now)
                    .set(PadInventorySheet::getCloseConclusion, "账实相符，无需闭环处理")
                    .set(PadInventorySheet::getCloseOperator, sheet.getInspector());
        }
        // 条件更新：仅盘点中单据可提交，并发下重复提交直接失败
        int rows = sheetMapper.update(null, update);
        if (rows == 0) {
            throw new RuntimeException("该盘点单已提交，禁止重复提交");
        }
        return sheetMapper.selectById(sheet.getId());
    }

    /**
     * 闭环：差异处理完毕后填写闭环结论（可登记闭环时间与闭环人，缺省取当前时间/盘点人）。
     * 仅“待闭环”单据可闭环，条件更新防止并发重复闭环；闭环后覆盖层位恢复可归还上架。
     */
    @Transactional(rollbackFor = Exception.class)
    public PadInventorySheet close(InventorySheetCloseDTO dto) {
        if (dto.getSheetId() == null) {
            throw new RuntimeException("盘点单不存在");
        }
        PadInventorySheet sheet = sheetMapper.selectById(dto.getSheetId());
        if (sheet == null) {
            throw new RuntimeException("盘点单不存在");
        }
        if (!PadInventorySheet.STATUS_SUBMITTED.equals(sheet.getStatus())) {
            throw new RuntimeException("仅待闭环的盘点单可闭环，请勿重复操作");
        }
        String conclusion = trim(dto.getCloseConclusion());
        if (conclusion.isEmpty()) {
            throw new RuntimeException("闭环必须填写处理结论");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime closeTime = dto.getCloseTime() != null ? dto.getCloseTime() : now;
        if (sheet.getSubmitTime() != null && closeTime.isBefore(sheet.getSubmitTime())) {
            throw new RuntimeException("闭环时间不能早于提交时间");
        }
        String closeOperator = trim(dto.getCloseOperator());
        if (closeOperator.isEmpty()) {
            closeOperator = sheet.getInspector();
        }

        LambdaUpdateWrapper<PadInventorySheet> update = new LambdaUpdateWrapper<>();
        update.eq(PadInventorySheet::getId, sheet.getId())
                .eq(PadInventorySheet::getStatus, PadInventorySheet.STATUS_SUBMITTED)
                .set(PadInventorySheet::getStatus, PadInventorySheet.STATUS_CLOSED)
                .set(PadInventorySheet::getCloseTime, closeTime)
                .set(PadInventorySheet::getCloseConclusion, conclusion)
                .set(PadInventorySheet::getCloseOperator, closeOperator)
                .set(PadInventorySheet::getUpdateTime, now);
        int rows = sheetMapper.update(null, update);
        if (rows == 0) {
            throw new RuntimeException("该盘点单已闭环，禁止重复操作");
        }
        return sheetMapper.selectById(sheet.getId());
    }

    /** 取消盘点：仅盘点中单据可取消；取消后不影响层位，可重新开单 */
    @Transactional(rollbackFor = Exception.class)
    public PadInventorySheet cancel(Long id) {
        PadInventorySheet sheet = sheetMapper.selectById(id);
        if (sheet == null) {
            throw new RuntimeException("盘点单不存在");
        }
        if (!PadInventorySheet.STATUS_IN_PROGRESS.equals(sheet.getStatus())) {
            throw new RuntimeException("仅盘点中的单据可取消");
        }
        LambdaUpdateWrapper<PadInventorySheet> update = new LambdaUpdateWrapper<>();
        update.eq(PadInventorySheet::getId, sheet.getId())
                .eq(PadInventorySheet::getStatus, PadInventorySheet.STATUS_IN_PROGRESS)
                .set(PadInventorySheet::getStatus, PadInventorySheet.STATUS_CANCELLED)
                .set(PadInventorySheet::getUpdateTime, LocalDateTime.now());
        int rows = sheetMapper.update(null, update);
        if (rows == 0) {
            throw new RuntimeException("仅盘点中的单据可取消");
        }
        return sheetMapper.selectById(sheet.getId());
    }

    public Map<String, Object> statistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("inProgressCount", sheetMapper.selectCount(new LambdaQueryWrapper<PadInventorySheet>()
                .eq(PadInventorySheet::getStatus, PadInventorySheet.STATUS_IN_PROGRESS)));
        stats.put("unclosedCount", sheetMapper.selectCount(new LambdaQueryWrapper<PadInventorySheet>()
                .eq(PadInventorySheet::getStatus, PadInventorySheet.STATUS_SUBMITTED)));
        stats.put("closedCount", sheetMapper.selectCount(new LambdaQueryWrapper<PadInventorySheet>()
                .eq(PadInventorySheet::getStatus, PadInventorySheet.STATUS_CLOSED)));
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        stats.put("monthSheetCount", sheetMapper.selectCount(new LambdaQueryWrapper<PadInventorySheet>()
                .ge(PadInventorySheet::getStartTime, monthStart)));
        return stats;
    }

    /** 盘点中单据校验：不存在或非盘点中均拒绝，返回单据供调用方继续使用 */
    private PadInventorySheet assertInProgress(Long sheetId) {
        PadInventorySheet sheet = sheetMapper.selectById(sheetId);
        if (sheet == null) {
            throw new RuntimeException("盘点单不存在");
        }
        if (!PadInventorySheet.STATUS_IN_PROGRESS.equals(sheet.getStatus())) {
            throw new RuntimeException("盘点单已提交或已取消，禁止再登记明细");
        }
        return sheet;
    }

    /** 盘点单号：PD + 日期时间 + 随机串，唯一约束兜底重试 */
    private String generateSheetNo() {
        String sheetNo;
        do {
            sheetNo = "PD" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
        } while (sheetMapper.selectCount(new LambdaQueryWrapper<PadInventorySheet>()
                .eq(PadInventorySheet::getSheetNo, sheetNo)) > 0);
        return sheetNo;
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
