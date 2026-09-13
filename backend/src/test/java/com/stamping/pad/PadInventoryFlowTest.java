package com.stamping.pad;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.dto.InventoryExtraItemDTO;
import com.stamping.pad.dto.InventoryItemMarkDTO;
import com.stamping.pad.dto.InventorySheetCloseDTO;
import com.stamping.pad.dto.InventorySheetCreateDTO;
import com.stamping.pad.dto.InventorySheetQueryDTO;
import com.stamping.pad.dto.InventorySheetSubmitDTO;
import com.stamping.pad.dto.PadCheckoutDTO;
import com.stamping.pad.dto.PadReturnDTO;
import com.stamping.pad.entity.PadBorrowRecord;
import com.stamping.pad.entity.PadInfo;
import com.stamping.pad.entity.PadInventoryItem;
import com.stamping.pad.entity.PadInventorySheet;
import com.stamping.pad.entity.ShelfLayer;
import com.stamping.pad.mapper.LayerAdjustRecordMapper;
import com.stamping.pad.mapper.PadBorrowRecordMapper;
import com.stamping.pad.mapper.PadInfoMapper;
import com.stamping.pad.mapper.PadInventoryItemMapper;
import com.stamping.pad.mapper.PadInventorySheetMapper;
import com.stamping.pad.mapper.ShelfLayerMapper;
import com.stamping.pad.service.PadBorrowService;
import com.stamping.pad.service.PadInventoryService;
import com.stamping.pad.service.ShelfLayerService;
import com.stamping.pad.vo.PadInventoryDetailVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 交班盘点链路：交班按货架层清点在架垫板（班次/盘点人），逐块标记相符/缺失、补录多出；
 * 有差异提交必须登记差异原因，单据进入“待闭环”（未平账）——覆盖层位在层位列表标出，
 * 未闭环前禁止归还上架；闭环必须填写处理结论，闭环后层位恢复可归还。
 */
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PadInventoryFlowTest {

    @Autowired
    private PadInventoryService inventoryService;
    @Autowired
    private ShelfLayerService shelfLayerService;
    @Autowired
    private PadBorrowService padBorrowService;
    @Autowired
    private ShelfLayerMapper shelfLayerMapper;
    @Autowired
    private PadInfoMapper padInfoMapper;
    @Autowired
    private PadBorrowRecordMapper borrowRecordMapper;
    @Autowired
    private PadInventorySheetMapper sheetMapper;
    @Autowired
    private PadInventoryItemMapper itemMapper;
    @Autowired
    private LayerAdjustRecordMapper recordMapper;

    @BeforeEach
    void setUp() {
        itemMapper.delete(null);
        sheetMapper.delete(null);
        borrowRecordMapper.delete(null);
        recordMapper.delete(null);
        padInfoMapper.delete(null);
        shelfLayerMapper.delete(null);
    }

    private ShelfLayer layer(String code, int capacity) {
        ShelfLayer l = new ShelfLayer();
        l.setLayerCode(code);
        l.setShelfCode(code.substring(0, code.lastIndexOf('-')));
        l.setLayerName(code + "层");
        l.setLayerOrder(1);
        l.setCapacity(capacity);
        shelfLayerMapper.insert(l);
        return l;
    }

    private Long padOnShelf(String code, String layerCode) {
        PadInfo p = new PadInfo();
        p.setPadCode(code);
        p.setMoldType("模具-" + code);
        p.setShelfLayerCode(layerCode);
        p.setBindTime(LocalDateTime.now());
        p.setMaintenanceStatus("AVAILABLE");
        p.setCreateTime(LocalDateTime.now());
        p.setUpdateTime(LocalDateTime.now());
        padInfoMapper.insert(p);
        return p.getId();
    }

    private InventorySheetCreateDTO layerSheetDto(String layerCode) {
        InventorySheetCreateDTO dto = new InventorySheetCreateDTO();
        dto.setScopeType("LAYER");
        dto.setLayerCode(layerCode);
        dto.setShift("DAY");
        dto.setInspector("王盘点");
        return dto;
    }

    private InventorySheetSubmitDTO submitDto(Long sheetId, String diffReason) {
        InventorySheetSubmitDTO dto = new InventorySheetSubmitDTO();
        dto.setSheetId(sheetId);
        dto.setDiffReason(diffReason);
        return dto;
    }

    private InventorySheetCloseDTO closeDto(Long sheetId, String conclusion) {
        InventorySheetCloseDTO dto = new InventorySheetCloseDTO();
        dto.setSheetId(sheetId);
        dto.setCloseConclusion(conclusion);
        return dto;
    }

    private void markAll(Long sheetId, String result) {
        List<PadInventoryItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PadInventoryItem>().eq(PadInventoryItem::getSheetId, sheetId));
        for (PadInventoryItem item : items) {
            InventoryItemMarkDTO mark = new InventoryItemMarkDTO();
            mark.setItemId(item.getId());
            mark.setCheckResult(result);
            inventoryService.markItem(mark);
        }
    }

    // ---------------- 开单 ----------------

    /** 按层位开单：快照该层在架垫板为账目明细，班次/盘点人落库，层位列表带出覆盖信息 */
    @Test
    void create_layerScope_snapshotsLedgerItems() {
        layer("A-01-01", 5);
        padOnShelf("P-001", "A-01-01");
        padOnShelf("P-002", "A-01-01");
        padOnShelf("P-003", null);

        InventorySheetCreateDTO dto = layerSheetDto("A-01-01");
        dto.setShift("NIGHT");
        PadInventorySheet sheet = inventoryService.create(dto);

        assertNotNull(sheet.getId());
        assertTrue(sheet.getSheetNo().startsWith("PD"));
        assertEquals("LAYER", sheet.getScopeType());
        assertEquals("A-01", sheet.getShelfCode());
        assertEquals("NIGHT", sheet.getShift());
        assertEquals("王盘点", sheet.getInspector());
        assertEquals("IN_PROGRESS", sheet.getStatus());
        assertEquals(List.of("A-01-01"), sheet.getCoveredLayerList());

        // 账目明细仅含在架的两块，未上架的 P-003 不入账
        PadInventoryDetailVO detail = inventoryService.getDetail(sheet.getId());
        assertEquals(2, detail.getItems().size());
        assertTrue(detail.getItems().stream().allMatch(i -> "LEDGER".equals(i.getItemType())));
        assertEquals(2, detail.getSheet().getTotalCount());
        assertEquals(0, detail.getSheet().getMarkedCount());
        // 覆盖层位实时占用与层位页同口径
        assertEquals(1, detail.getLayers().size());
        assertEquals(2, detail.getLayers().get(0).get("padCount"));
    }

    /** 按货架开单：覆盖该货架全部层位；同一层位仅允许一张盘点中单据 */
    @Test
    void create_shelfScope_coversAllLayers_andRejectsOverlap() {
        layer("A-01-01", 5);
        layer("A-01-02", 5);
        layer("B-01-01", 5);

        InventorySheetCreateDTO dto = new InventorySheetCreateDTO();
        dto.setScopeType("SHELF");
        dto.setShelfCode("A-01");
        dto.setShift("DAY");
        dto.setInspector("李盘点");
        PadInventorySheet sheet = inventoryService.create(dto);

        assertEquals(List.of("A-01-01", "A-01-02"), sheet.getCoveredLayerList());
        assertNull(sheet.getLayerCode());

        // 覆盖层位存在盘点中单据：按层位/按货架再开单均被拒
        RuntimeException ex1 = assertThrows(RuntimeException.class,
                () -> inventoryService.create(layerSheetDto("A-01-01")));
        assertTrue(ex1.getMessage().contains("盘点中"));
        InventorySheetCreateDTO dto2 = new InventorySheetCreateDTO();
        dto2.setScopeType("SHELF");
        dto2.setShelfCode("A-01");
        dto2.setShift("MIDDLE");
        dto2.setInspector("李盘点");
        assertThrows(RuntimeException.class, () -> inventoryService.create(dto2));

        // 不重叠的货架可正常开单
        InventorySheetCreateDTO dto3 = new InventorySheetCreateDTO();
        dto3.setScopeType("SHELF");
        dto3.setShelfCode("B-01");
        dto3.setShift("MIDDLE");
        dto3.setInspector("李盘点");
        assertNotNull(inventoryService.create(dto3).getId());
        assertEquals(2, sheetMapper.selectCount(null));
    }

    /** 开单必填校验：范围/班次/盘点人缺失或非法时拒绝 */
    @Test
    void create_missingRequiredFields_rejected() {
        layer("A-01-01", 5);

        InventorySheetCreateDTO noShift = layerSheetDto("A-01-01");
        noShift.setShift("UNKNOWN");
        assertThrows(RuntimeException.class, () -> inventoryService.create(noShift));

        InventorySheetCreateDTO noInspector = layerSheetDto("A-01-01");
        noInspector.setInspector(" ");
        assertThrows(RuntimeException.class, () -> inventoryService.create(noInspector));

        InventorySheetCreateDTO noLayer = layerSheetDto("B-99-99");
        assertThrows(RuntimeException.class, () -> inventoryService.create(noLayer));

        InventorySheetCreateDTO emptyShelf = new InventorySheetCreateDTO();
        emptyShelf.setScopeType("SHELF");
        emptyShelf.setShelfCode("B-99");
        emptyShelf.setShift("DAY");
        emptyShelf.setInspector("王盘点");
        assertThrows(RuntimeException.class, () -> inventoryService.create(emptyShelf));

        assertEquals(0, sheetMapper.selectCount(null));
    }

    // ---------------- 标记与补录 ----------------

    /** 补录多出：层位须在覆盖范围内、编号不可与账目清单重复、同层同编号不可重复补录 */
    @Test
    void addExtraItem_validations() {
        layer("A-01-01", 5);
        layer("A-01-02", 5);
        padOnShelf("P-001", "A-01-01");
        PadInventorySheet sheet = inventoryService.create(layerSheetDto("A-01-01"));

        // 层位不在覆盖范围：拒绝
        InventoryExtraItemDTO wrongLayer = new InventoryExtraItemDTO();
        wrongLayer.setLayerCode("A-01-02");
        wrongLayer.setPadCode("X-001");
        assertThrows(RuntimeException.class, () -> inventoryService.addExtraItem(sheet.getId(), wrongLayer));

        // 编号已在账目清单：拒绝，应直接标记
        InventoryExtraItemDTO inLedger = new InventoryExtraItemDTO();
        inLedger.setLayerCode("A-01-01");
        inLedger.setPadCode("P-001");
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> inventoryService.addExtraItem(sheet.getId(), inLedger));
        assertTrue(ex.getMessage().contains("账目清单"));

        // 正常补录：能匹配档案时回填垫板ID
        padOnShelf("P-009", "A-01-02");
        InventoryExtraItemDTO extra = new InventoryExtraItemDTO();
        extra.setLayerCode("A-01-01");
        extra.setPadCode("P-009");
        PadInventoryItem saved = inventoryService.addExtraItem(sheet.getId(), extra);
        assertEquals("EXTRA", saved.getCheckResult());
        assertNotNull(saved.getPadId());

        // 同层同编号重复补录：拒绝
        assertThrows(RuntimeException.class, () -> inventoryService.addExtraItem(sheet.getId(), extra));

        // 多出明细不参与逐块标记
        InventoryItemMarkDTO markExtra = new InventoryItemMarkDTO();
        markExtra.setItemId(saved.getId());
        markExtra.setCheckResult("MATCH");
        assertThrows(RuntimeException.class, () -> inventoryService.markItem(markExtra));

        // 删除补录后可重新补录
        inventoryService.removeExtraItem(saved.getId());
        assertNotNull(inventoryService.addExtraItem(sheet.getId(), extra).getId());
    }

    /** 提交前全部账目明细必须标记完毕 */
    @Test
    void submit_unmarkedItems_rejected() {
        layer("A-01-01", 5);
        padOnShelf("P-001", "A-01-01");
        padOnShelf("P-002", "A-01-01");
        PadInventorySheet sheet = inventoryService.create(layerSheetDto("A-01-01"));

        List<PadInventoryItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PadInventoryItem>().eq(PadInventoryItem::getSheetId, sheet.getId()));
        InventoryItemMarkDTO mark = new InventoryItemMarkDTO();
        mark.setItemId(items.get(0).getId());
        mark.setCheckResult("MATCH");
        inventoryService.markItem(mark);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> inventoryService.submit(submitDto(sheet.getId(), null)));
        assertTrue(ex.getMessage().contains("未标记"));
        assertEquals("IN_PROGRESS", sheetMapper.selectById(sheet.getId()).getStatus());
    }

    // ---------------- 提交与未平账 ----------------

    /** 账实相符：提交即自动闭环，层位不出现未平账 */
    @Test
    void submit_balanced_autoCloses() {
        layer("A-01-01", 5);
        padOnShelf("P-001", "A-01-01");
        PadInventorySheet sheet = inventoryService.create(layerSheetDto("A-01-01"));
        markAll(sheet.getId(), "MATCH");

        PadInventorySheet submitted = inventoryService.submit(submitDto(sheet.getId(), null));
        assertEquals("CLOSED", submitted.getStatus());
        assertNotNull(submitted.getSubmitTime());
        assertNotNull(submitted.getCloseTime());
        assertEquals("账实相符，无需闭环处理", submitted.getCloseConclusion());
        assertEquals("王盘点", submitted.getCloseOperator());

        // 层位未平账标记为空，层位列表/单查一致
        assertNull(shelfLayerService.getByCode("A-01-01").getActiveUnbalanced());
        assertTrue(shelfLayerService.listAll().stream()
                .noneMatch(l -> l.getActiveUnbalanced() != null));
    }

    /** 有差异提交必须登记差异原因；提交后层位未平账，概览/层位页同源标出 */
    @Test
    void submit_withDiff_requiresReason_andMarksLayerUnbalanced() {
        layer("A-01-01", 5);
        padOnShelf("P-001", "A-01-01");
        PadInventorySheet sheet = inventoryService.create(layerSheetDto("A-01-01"));
        markAll(sheet.getId(), "MISSING");

        // 差异原因缺失：拒绝
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> inventoryService.submit(submitDto(sheet.getId(), "  ")));
        assertTrue(ex.getMessage().contains("差异原因"));
        assertEquals("IN_PROGRESS", sheetMapper.selectById(sheet.getId()).getStatus());

        PadInventorySheet submitted = inventoryService.submit(
                submitDto(sheet.getId(), "P-001 疑似随模具带出未登记"));
        assertEquals("SUBMITTED", submitted.getStatus());
        assertEquals("P-001 疑似随模具带出未登记", submitted.getDiffReason());
        assertNotNull(submitted.getSubmitTime());

        // 重复提交：拒绝
        assertThrows(RuntimeException.class,
                () -> inventoryService.submit(submitDto(sheet.getId(), "再次提交")));

        // 层位单查/列表均带出未平账单据与差异原因（概览/层位页/归还弹窗同源）
        ShelfLayer byCode = shelfLayerService.getByCode("A-01-01");
        assertNotNull(byCode.getActiveUnbalanced());
        assertEquals("P-001 疑似随模具带出未登记", byCode.getActiveUnbalanced().getDiffReason());
        assertEquals(submitted.getSheetNo(), byCode.getActiveUnbalanced().getSheetNo());
        ShelfLayer fromList = shelfLayerService.listAll().stream()
                .filter(l -> "A-01-01".equals(l.getLayerCode())).findFirst().orElseThrow();
        assertNotNull(fromList.getActiveUnbalanced());
    }

    /** 未闭环前禁止往该层归还上架；闭环后恢复，归还成功 */
    @Test
    void unbalancedLayer_rejectsReturn_untilClosed() {
        layer("A-01-01", 5);
        layer("B-01-01", 5);
        Long padId = padOnShelf("P-001", "B-01-01");

        // 空层 A-01-01 盘点补录一块多出 → 有差异提交 → 未平账
        PadInventorySheet sheet = inventoryService.create(layerSheetDto("A-01-01"));
        InventoryExtraItemDTO extra = new InventoryExtraItemDTO();
        extra.setLayerCode("A-01-01");
        extra.setPadCode("X-100");
        inventoryService.addExtraItem(sheet.getId(), extra);
        inventoryService.submit(submitDto(sheet.getId(), "现场多出一块无账垫板，待核查"));

        // 领用 B-01-01 的垫板，尝试归还到未平账层 A-01-01：拒绝
        PadCheckoutDTO checkout = new PadCheckoutDTO();
        checkout.setPadId(padId);
        checkout.setBorrower("张三");
        checkout.setProductionLine("冲压一线");
        PadBorrowRecord record = padBorrowService.checkout(checkout);

        PadReturnDTO ret = new PadReturnDTO();
        ret.setId(record.getId());
        ret.setReturnLayerCode("A-01-01");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> padBorrowService.doReturn(ret));
        assertTrue(ex.getMessage().contains("未闭环"));
        assertTrue(ex.getMessage().contains("现场多出一块无账垫板"));
        assertEquals("BORROWED", borrowRecordMapper.selectById(record.getId()).getStatus());

        // 闭环必须填写结论
        assertThrows(RuntimeException.class, () -> inventoryService.close(closeDto(sheet.getId(), " ")));

        // 闭环后层位恢复：归还成功
        inventoryService.close(closeDto(sheet.getId(), "多出垫板已登记退库，账实一致"));
        assertNull(shelfLayerService.getByCode("A-01-01").getActiveUnbalanced());
        PadBorrowRecord returned = padBorrowService.doReturn(ret);
        assertEquals("RETURNED", returned.getStatus());
        assertEquals("A-01-01", padInfoMapper.selectById(padId).getShelfLayerCode());
    }

    // ---------------- 闭环 ----------------

    /** 闭环校验：仅待闭环可闭环、闭环时间不早于提交时间、禁止重复闭环 */
    @Test
    void close_validations() {
        layer("A-01-01", 5);
        padOnShelf("P-001", "A-01-01");
        PadInventorySheet sheet = inventoryService.create(layerSheetDto("A-01-01"));
        markAll(sheet.getId(), "MISSING");

        // 盘点中单据不可闭环
        assertThrows(RuntimeException.class, () -> inventoryService.close(closeDto(sheet.getId(), "结论")));

        inventoryService.submit(submitDto(sheet.getId(), "缺失一块，待核查"));

        // 闭环时间早于提交时间：拒绝
        InventorySheetCloseDTO earlyClose = closeDto(sheet.getId(), "已找回");
        earlyClose.setCloseTime(LocalDateTime.now().minusDays(1));
        assertThrows(RuntimeException.class, () -> inventoryService.close(earlyClose));

        // 正常闭环：缺省闭环人取盘点人
        PadInventorySheet closed = inventoryService.close(closeDto(sheet.getId(), "缺失板已找回上架"));
        assertEquals("CLOSED", closed.getStatus());
        assertEquals("缺失板已找回上架", closed.getCloseConclusion());
        assertEquals("王盘点", closed.getCloseOperator());
        assertNotNull(closed.getCloseTime());

        // 重复闭环：拒绝
        assertThrows(RuntimeException.class, () -> inventoryService.close(closeDto(sheet.getId(), "再次闭环")));
    }

    // ---------------- 取消 ----------------

    /** 盘点中单据可取消；取消后层位可重新开单，已提交单据不可取消 */
    @Test
    void cancel_inProgress_allowsRecreate() {
        layer("A-01-01", 5);
        padOnShelf("P-001", "A-01-01");
        PadInventorySheet sheet = inventoryService.create(layerSheetDto("A-01-01"));

        PadInventorySheet cancelled = inventoryService.cancel(sheet.getId());
        assertEquals("CANCELLED", cancelled.getStatus());
        assertNull(cancelled.getSubmitTime());

        // 取消后可重新开单
        PadInventorySheet recreated = inventoryService.create(layerSheetDto("A-01-01"));
        assertNotNull(recreated.getId());

        // 已提交单据不可取消
        markAll(recreated.getId(), "MISSING");
        inventoryService.submit(submitDto(recreated.getId(), "缺失待核查"));
        assertThrows(RuntimeException.class, () -> inventoryService.cancel(recreated.getId()));
    }

    // ---------------- 台账回看 ----------------

    /** 台账按状态/班次/覆盖层位/单号筛选，统计卡片口径一致 */
    @Test
    void pageList_filtersAndStatistics() {
        layer("A-01-01", 5);
        layer("A-01-02", 5);
        layer("B-01-01", 5);
        padOnShelf("P-001", "A-01-01");

        // 待闭环单（覆盖 A-01-01，有缺失差异）
        PadInventorySheet unclosed = inventoryService.create(layerSheetDto("A-01-01"));
        markAll(unclosed.getId(), "MISSING");
        inventoryService.submit(submitDto(unclosed.getId(), "缺失一块"));

        // 已闭环单（覆盖 A-01-02，账实相符自动闭环）
        PadInventorySheet closed = inventoryService.create(layerSheetDto("A-01-02"));
        inventoryService.submit(submitDto(closed.getId(), null));

        // 盘点中单（覆盖 B-01-01）
        inventoryService.create(layerSheetDto("B-01-01"));

        // 按状态筛选
        InventorySheetQueryDTO submittedQuery = new InventorySheetQueryDTO();
        submittedQuery.setStatus("SUBMITTED");
        Page<PadInventorySheet> submittedPage = inventoryService.pageList(submittedQuery);
        assertEquals(1, submittedPage.getTotal());
        assertEquals("缺失一块", submittedPage.getRecords().get(0).getDiffReason());
        assertEquals(1, submittedPage.getRecords().get(0).getMissingCount());

        InventorySheetQueryDTO closedQuery = new InventorySheetQueryDTO();
        closedQuery.setStatus("CLOSED");
        assertEquals(1, inventoryService.pageList(closedQuery).getTotal());

        InventorySheetQueryDTO progressQuery = new InventorySheetQueryDTO();
        progressQuery.setStatus("IN_PROGRESS");
        assertEquals(1, inventoryService.pageList(progressQuery).getTotal());

        // 按覆盖层位筛选：A-01-01 仅命中待闭环单
        InventorySheetQueryDTO layerQuery = new InventorySheetQueryDTO();
        layerQuery.setLayerCode("A-01-01");
        Page<PadInventorySheet> layerPage = inventoryService.pageList(layerQuery);
        assertEquals(1, layerPage.getTotal());
        assertEquals(unclosed.getSheetNo(), layerPage.getRecords().get(0).getSheetNo());

        // 按班次 + 单号模糊筛选
        InventorySheetQueryDTO shiftQuery = new InventorySheetQueryDTO();
        shiftQuery.setShift("DAY");
        assertEquals(3, inventoryService.pageList(shiftQuery).getTotal());
        InventorySheetQueryDTO noQuery = new InventorySheetQueryDTO();
        noQuery.setSheetNo(unclosed.getSheetNo().substring(0, 19));
        Page<PadInventorySheet> noPage = inventoryService.pageList(noQuery);
        assertEquals(1, noPage.getTotal());
        assertEquals(unclosed.getSheetNo(), noPage.getRecords().get(0).getSheetNo());

        // 统计：盘点中/待闭环/已闭环/本月
        assertEquals(1L, inventoryService.statistics().get("inProgressCount"));
        assertEquals(1L, inventoryService.statistics().get("unclosedCount"));
        assertEquals(1L, inventoryService.statistics().get("closedCount"));
        assertEquals(3L, inventoryService.statistics().get("monthSheetCount"));
    }
}
