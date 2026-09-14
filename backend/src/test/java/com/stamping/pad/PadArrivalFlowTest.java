package com.stamping.pad;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.dto.BindLayerDTO;
import com.stamping.pad.dto.PadArrivalDTO;
import com.stamping.pad.dto.PadArrivalItemDTO;
import com.stamping.pad.dto.PadArrivalPassDTO;
import com.stamping.pad.dto.PadArrivalQueryDTO;
import com.stamping.pad.dto.PadArrivalRejectDTO;
import com.stamping.pad.dto.PadCheckoutDTO;
import com.stamping.pad.dto.PadInfoDTO;
import com.stamping.pad.dto.PadMaintenanceDTO;
import com.stamping.pad.dto.PadMoldReserveDTO;
import com.stamping.pad.dto.PadQueryDTO;
import com.stamping.pad.dto.UnbindLayerDTO;
import com.stamping.pad.entity.LayerAdjustRecord;
import com.stamping.pad.entity.PadArrivalBatch;
import com.stamping.pad.entity.PadArrivalItem;
import com.stamping.pad.entity.PadInfo;
import com.stamping.pad.entity.ShelfLayer;
import com.stamping.pad.mapper.LayerAdjustRecordMapper;
import com.stamping.pad.mapper.LayerBlockRecordMapper;
import com.stamping.pad.mapper.LayerCapacityExpandRecordMapper;
import com.stamping.pad.mapper.PadArrivalBatchMapper;
import com.stamping.pad.mapper.PadArrivalItemMapper;
import com.stamping.pad.mapper.PadBorrowRecordMapper;
import com.stamping.pad.mapper.PadInfoMapper;
import com.stamping.pad.mapper.PadInventoryItemMapper;
import com.stamping.pad.mapper.PadInventorySheetMapper;
import com.stamping.pad.mapper.PadMaintenanceRecordMapper;
import com.stamping.pad.mapper.PadMoldReserveItemMapper;
import com.stamping.pad.mapper.PadMoldReserveRecordMapper;
import com.stamping.pad.mapper.PadScrapRecordMapper;
import com.stamping.pad.mapper.ShelfLayerMapper;
import com.stamping.pad.service.PadArrivalService;
import com.stamping.pad.service.PadBorrowService;
import com.stamping.pad.service.PadInfoService;
import com.stamping.pad.service.PadMaintenanceService;
import com.stamping.pad.service.PadMoldReserveService;
import com.stamping.pad.service.ShelfLayerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 到货待检（待检层）链路：新到垫板先落待检层（登记到货批次、到货时间、经办人），
 * 待检层垫板不可领用/换绑/计入可用库存；质检通过整批转正式层（逐块上架并写 BIND 记录），
 * 判退整批离库（档案冻结）；数据概览单独标出待检占用。
 */
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PadArrivalFlowTest {

    @Autowired
    private PadArrivalService arrivalService;
    @Autowired
    private PadInfoService padInfoService;
    @Autowired
    private PadBorrowService padBorrowService;
    @Autowired
    private PadMaintenanceService padMaintenanceService;
    @Autowired
    private PadMoldReserveService padMoldReserveService;
    @Autowired
    private ShelfLayerService shelfLayerService;
    @Autowired
    private ShelfLayerMapper shelfLayerMapper;
    @Autowired
    private PadInfoMapper padInfoMapper;
    @Autowired
    private PadArrivalBatchMapper batchMapper;
    @Autowired
    private PadArrivalItemMapper itemMapper;
    @Autowired
    private PadBorrowRecordMapper borrowRecordMapper;
    @Autowired
    private PadMaintenanceRecordMapper maintenanceRecordMapper;
    @Autowired
    private PadMoldReserveRecordMapper reserveRecordMapper;
    @Autowired
    private PadMoldReserveItemMapper reserveItemMapper;
    @Autowired
    private LayerAdjustRecordMapper recordMapper;
    @Autowired
    private LayerBlockRecordMapper blockRecordMapper;
    @Autowired
    private LayerCapacityExpandRecordMapper expandRecordMapper;
    @Autowired
    private PadScrapRecordMapper scrapRecordMapper;
    @Autowired
    private PadInventorySheetMapper inventorySheetMapper;
    @Autowired
    private PadInventoryItemMapper inventoryItemMapper;

    // 各测试类共享同一 H2 库：清理全部相关业务表，避免残留封锁/扩容等记录污染容量与上架校验
    @BeforeEach
    void setUp() {
        itemMapper.delete(null);
        batchMapper.delete(null);
        reserveItemMapper.delete(null);
        reserveRecordMapper.delete(null);
        maintenanceRecordMapper.delete(null);
        borrowRecordMapper.delete(null);
        scrapRecordMapper.delete(null);
        inventoryItemMapper.delete(null);
        inventorySheetMapper.delete(null);
        blockRecordMapper.delete(null);
        expandRecordMapper.delete(null);
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

    private PadArrivalItemDTO item(String padCode) {
        PadArrivalItemDTO dto = new PadArrivalItemDTO();
        dto.setPadCode(padCode);
        dto.setMoldType("模具-" + padCode);
        return dto;
    }

    private PadArrivalDTO arrivalDto(String... padCodes) {
        PadArrivalDTO dto = new PadArrivalDTO();
        dto.setOperator("王库管");
        dto.setSupplier("华东五金");
        dto.setArrivalTime(LocalDateTime.now());
        dto.setItems(java.util.Arrays.stream(padCodes).map(this::item).toList());
        return dto;
    }

    private PadArrivalPassDTO passDto(Long batchId, List<PadArrivalItem> items, String layerCode) {
        PadArrivalPassDTO dto = new PadArrivalPassDTO();
        dto.setId(batchId);
        dto.setInspectConclusion("外观尺寸抽检合格");
        dto.setTargets(items.stream().map(i -> {
            PadArrivalPassDTO.Target t = new PadArrivalPassDTO.Target();
            t.setPadId(i.getPadId());
            t.setLayerCode(layerCode);
            return t;
        }).toList());
        return dto;
    }

    // ---------------- 登记到货 ----------------

    /** 登记到货：写批次（批次号/到货时间/经办人）+ 明细 + 垫板档案（待检层、无层位、不计可用库存） */
    @Test
    void register_createsBatchItemsAndQuarantinePads() {
        PadArrivalBatch batch = arrivalService.register(arrivalDto("P-101", "P-102", "P-103"));
        assertNotNull(batch.getId());
        assertTrue(batch.getBatchNo().startsWith("DH"));
        assertEquals("PENDING", batch.getStatus());
        assertEquals("王库管", batch.getOperator());
        assertEquals("华东五金", batch.getSupplier());
        assertEquals(3, batch.getPadCount());
        assertEquals(3, batch.getItems().size());

        // 批内垫板全部落待检层：QUARANTINE、无层位、保养状态默认可用
        List<PadInfo> pads = padInfoMapper.selectList(null);
        assertEquals(3, pads.size());
        pads.forEach(p -> {
            assertEquals("QUARANTINE", p.getStockStatus());
            assertNull(p.getShelfLayerCode());
            assertNull(p.getBindTime());
            assertEquals("AVAILABLE", p.getMaintenanceStatus());
        });

        // 待检占用单独统计：3 块待检、1 个待检批次；不算未绑定正式库存口径之外的可用库存
        assertEquals(3L, arrivalService.statistics().get("pendingPadCount"));
        assertEquals(1L, arrivalService.statistics().get("pendingCount"));
        assertEquals(1L, arrivalService.statistics().get("monthArrivalCount"));
    }

    /** 必填/重复校验：经办人、明细缺失，批次内编号重复、与档案库编号重复均拒绝，不落任何数据 */
    @Test
    void register_invalidFields_rejected() {
        PadArrivalDTO noOperator = arrivalDto("P-101");
        noOperator.setOperator("  ");
        assertThrows(RuntimeException.class, () -> arrivalService.register(noOperator));

        PadArrivalDTO noItems = arrivalDto();
        assertThrows(RuntimeException.class, () -> arrivalService.register(noItems));

        PadArrivalDTO dupInBatch = arrivalDto("P-101", "P-101");
        assertThrows(RuntimeException.class, () -> arrivalService.register(dupInBatch));

        // 档案库已有编号：拒绝重复到货登记
        PadInfoDTO existing = new PadInfoDTO();
        existing.setPadCode("P-OLD");
        padInfoService.create(existing);
        PadArrivalDTO dupWithArchive = arrivalDto("P-OLD");
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> arrivalService.register(dupWithArchive));
        assertTrue(ex.getMessage().contains("P-OLD"));

        assertEquals(0, batchMapper.selectCount(null));
        assertEquals(0, itemMapper.selectCount(null));
        // 仅有常规建档的 P-OLD（正式在库），待检占用为 0
        assertEquals(1, padInfoMapper.selectCount(null));
        assertEquals(0L, arrivalService.statistics().get("pendingPadCount"));
    }

    // ---------------- 待检层约束 ----------------

    /** 待检层垫板不可领用、绑定/换绑/解绑、编辑换层、删除、保养、预留，判退前只能等质检判定 */
    @Test
    void quarantinePads_blockedFromAllStockOperations() {
        layer("A-01-01", 10);
        PadArrivalBatch batch = arrivalService.register(arrivalDto("P-101"));
        Long padId = batch.getItems().get(0).getPadId();

        // 领用：拒绝
        PadCheckoutDTO checkout = new PadCheckoutDTO();
        checkout.setPadId(padId);
        checkout.setBorrower("张三");
        checkout.setProductionLine("冲压一线");
        RuntimeException checkoutEx = assertThrows(RuntimeException.class,
                () -> padBorrowService.checkout(checkout));
        assertTrue(checkoutEx.getMessage().contains("待检"));

        // 绑定/换绑：拒绝
        BindLayerDTO bind = new BindLayerDTO();
        bind.setPadId(padId);
        bind.setLayerCode("A-01-01");
        assertThrows(RuntimeException.class, () -> padInfoService.bindLayer(bind));

        // 解绑：拒绝
        UnbindLayerDTO unbind = new UnbindLayerDTO();
        unbind.setPadId(padId);
        assertThrows(RuntimeException.class, () -> padInfoService.unbindLayer(unbind));

        // 编辑换层：拒绝（非层位编辑允许，例如修正备注）
        PadInfoDTO editLayer = new PadInfoDTO();
        editLayer.setId(padId);
        editLayer.setPadCode("P-101");
        editLayer.setShelfLayerCode("A-01-01");
        assertThrows(RuntimeException.class, () -> padInfoService.update(editLayer));
        PadInfoDTO editRemark = new PadInfoDTO();
        editRemark.setId(padId);
        editRemark.setPadCode("P-101");
        editRemark.setRemark("供应商补送配件");
        padInfoService.update(editRemark);
        assertEquals("供应商补送配件", padInfoMapper.selectById(padId).getRemark());

        // 删除：拒绝
        assertThrows(RuntimeException.class, () -> padInfoService.delete(padId));

        // 保养登记：拒绝
        PadMaintenanceDTO maintenance = new PadMaintenanceDTO();
        maintenance.setPadId(padId);
        maintenance.setMaintenanceType("日常保养");
        maintenance.setHandler("李保养");
        maintenance.setMaintenanceResult("NORMAL");
        maintenance.setStatusAfter("AVAILABLE");
        assertThrows(RuntimeException.class, () -> padMaintenanceService.register(maintenance));

        // 换模预留：拒绝
        PadMoldReserveDTO reserve = new PadMoldReserveDTO();
        reserve.setMoldCode("MOLD-01");
        reserve.setPadIds(List.of(padId));
        reserve.setStartTime(LocalDateTime.now());
        reserve.setEndTime(LocalDateTime.now().plusDays(1));
        reserve.setOperator("王库管");
        assertThrows(RuntimeException.class, () -> padMoldReserveService.register(reserve));

        // 档案分页实时带出待检状态，层位占用不受影响
        Page<PadInfo> page = padInfoMapper.selectPageList(new Page<>(1, 10), new PadQueryDTO());
        assertEquals("QUARANTINE", page.getRecords().get(0).getStockStatus());
        assertEquals(0, shelfLayerService.getByCode("A-01-01").getPadCount());
    }

    // ---------------- 质检通过 ----------------

    /** 质检通过：整批转正式层（逐块上架、写 BIND 调整记录、明细记录目标层位），批次落 PASSED */
    @Test
    void passInspection_movesWholeBatchToFormalLayers() {
        layer("A-01-01", 10);
        layer("A-01-02", 10);
        PadArrivalBatch batch = arrivalService.register(arrivalDto("P-101", "P-102"));
        List<PadArrivalItem> items = batch.getItems();

        // 逐块指定目标层位：P-101 -> A-01-01，P-102 -> A-01-02
        PadArrivalPassDTO dto = new PadArrivalPassDTO();
        dto.setId(batch.getId());
        dto.setInspectConclusion("抽检合格，同意入库");
        dto.setInspector("赵质检");
        dto.setTargets(List.of(
                target(items.get(0).getPadId(), "A-01-01"),
                target(items.get(1).getPadId(), "A-01-02")));
        PadArrivalBatch passed = arrivalService.passInspection(dto);

        assertEquals("PASSED", passed.getStatus());
        assertEquals("赵质检", passed.getInspector());
        assertNotNull(passed.getInspectTime());
        assertEquals("抽检合格，同意入库", passed.getInspectConclusion());

        // 垫板转正式在库并上架，绑定时间为质检时间
        PadInfo p1 = padInfoMapper.selectById(items.get(0).getPadId());
        PadInfo p2 = padInfoMapper.selectById(items.get(1).getPadId());
        assertEquals("OFFICIAL", p1.getStockStatus());
        assertEquals("A-01-01", p1.getShelfLayerCode());
        assertNotNull(p1.getBindTime());
        assertEquals("OFFICIAL", p2.getStockStatus());
        assertEquals("A-01-02", p2.getShelfLayerCode());

        // 层位占用实时增长，待检占用清零
        assertEquals(1, shelfLayerService.getByCode("A-01-01").getPadCount());
        assertEquals(1, shelfLayerService.getByCode("A-01-02").getPadCount());
        assertEquals(0L, arrivalService.statistics().get("pendingPadCount"));
        assertEquals(1L, arrivalService.statistics().get("passedCount"));

        // 每块写入 BIND 调整记录，明细记录目标层位
        List<LayerAdjustRecord> records = recordMapper.selectList(null);
        assertEquals(2, records.size());
        assertTrue(records.stream().allMatch(r -> "BIND".equals(r.getAdjustType())));
        assertTrue(records.stream().allMatch(r -> r.getAdjustReason().contains(batch.getBatchNo())));
        List<PadArrivalItem> savedItems = itemMapper.selectByBatchId(batch.getId());
        assertEquals("A-01-01", savedItems.get(0).getTargetLayerCode());
        assertEquals("A-01-02", savedItems.get(1).getTargetLayerCode());

        // 转正式后恢复可领用
        PadCheckoutDTO checkout = new PadCheckoutDTO();
        checkout.setPadId(p1.getId());
        checkout.setBorrower("张三");
        checkout.setProductionLine("冲压一线");
        padBorrowService.checkout(checkout);
        assertNull(padInfoMapper.selectById(p1.getId()).getShelfLayerCode());
    }

    /** 质检通过校验：目标层位缺块/容量不足/重复判定均拒绝 */
    @Test
    void passInspection_validatesTargetsCapacityAndRepeat() {
        layer("A-01-01", 1);
        PadArrivalBatch batch = arrivalService.register(arrivalDto("P-101", "P-102"));
        List<PadArrivalItem> items = batch.getItems();

        // 漏块：P-102 未指定目标层位
        PadArrivalPassDTO missing = new PadArrivalPassDTO();
        missing.setId(batch.getId());
        missing.setInspectConclusion("合格");
        missing.setTargets(List.of(target(items.get(0).getPadId(), "A-01-01")));
        assertThrows(RuntimeException.class, () -> arrivalService.passInspection(missing));

        // 容量不足：A-01-01 配额 1，整批 2 块同层放不下
        PadArrivalPassDTO overCapacity = passDto(batch.getId(), items, "A-01-01");
        RuntimeException capEx = assertThrows(RuntimeException.class,
                () -> arrivalService.passInspection(overCapacity));
        assertTrue(capEx.getMessage().contains("已满"));

        // 空结论：拒绝
        PadArrivalPassDTO blank = passDto(batch.getId(), items, "A-01-01");
        blank.setInspectConclusion(" ");
        assertThrows(RuntimeException.class, () -> arrivalService.passInspection(blank));

        // 批次与垫板保持待检原状
        assertEquals("PENDING", batchMapper.selectById(batch.getId()).getStatus());
        padInfoMapper.selectList(null).forEach(p -> assertEquals("QUARANTINE", p.getStockStatus()));

        // 分批上架：P-101 -> A-01-01（占满），P-102 -> A-01-02
        layer("A-01-02", 10);
        PadArrivalPassDTO ok = new PadArrivalPassDTO();
        ok.setId(batch.getId());
        ok.setInspectConclusion("合格");
        ok.setTargets(List.of(
                target(items.get(0).getPadId(), "A-01-01"),
                target(items.get(1).getPadId(), "A-01-02")));
        arrivalService.passInspection(ok);

        // 重复判定：拒绝
        assertThrows(RuntimeException.class, () -> arrivalService.passInspection(ok));
        PadArrivalRejectDTO reject = new PadArrivalRejectDTO();
        reject.setId(batch.getId());
        reject.setInspectConclusion("重复判退");
        assertThrows(RuntimeException.class, () -> arrivalService.reject(reject));
    }

    // ---------------- 判退离库 ----------------

    /** 判退：整批离库（stock_status=REJECTED、档案冻结），批次落 REJECTED，判退后一切库存操作禁止 */
    @Test
    void reject_movesWholeBatchOutAndFreezes() {
        layer("A-01-01", 10);
        PadArrivalBatch batch = arrivalService.register(arrivalDto("P-101", "P-102"));
        List<PadArrivalItem> items = batch.getItems();

        // 空判退结论：拒绝
        PadArrivalRejectDTO blank = new PadArrivalRejectDTO();
        blank.setId(batch.getId());
        blank.setInspectConclusion(" ");
        assertThrows(RuntimeException.class, () -> arrivalService.reject(blank));

        PadArrivalRejectDTO dto = new PadArrivalRejectDTO();
        dto.setId(batch.getId());
        dto.setInspectConclusion("尺寸超差，整批退回供应商");
        PadArrivalBatch rejected = arrivalService.reject(dto);
        assertEquals("REJECTED", rejected.getStatus());
        assertEquals("王库管", rejected.getInspector());
        assertNotNull(rejected.getInspectTime());

        // 垫板判退离库：REJECTED、无层位，待检占用清零
        padInfoMapper.selectList(null).forEach(p -> {
            assertEquals("REJECTED", p.getStockStatus());
            assertNull(p.getShelfLayerCode());
        });
        assertEquals(0L, arrivalService.statistics().get("pendingPadCount"));
        assertEquals(1L, arrivalService.statistics().get("rejectedCount"));

        Long padId = items.get(0).getPadId();
        // 判退后：领用/绑定/编辑/删除/保养全部冻结
        PadCheckoutDTO checkout = new PadCheckoutDTO();
        checkout.setPadId(padId);
        checkout.setBorrower("张三");
        checkout.setProductionLine("冲压一线");
        assertThrows(RuntimeException.class, () -> padBorrowService.checkout(checkout));

        BindLayerDTO bind = new BindLayerDTO();
        bind.setPadId(padId);
        bind.setLayerCode("A-01-01");
        assertThrows(RuntimeException.class, () -> padInfoService.bindLayer(bind));

        PadInfoDTO edit = new PadInfoDTO();
        edit.setId(padId);
        edit.setPadCode("P-101");
        assertThrows(RuntimeException.class, () -> padInfoService.update(edit));
        assertThrows(RuntimeException.class, () -> padInfoService.delete(padId));

        PadMaintenanceDTO maintenance = new PadMaintenanceDTO();
        maintenance.setPadId(padId);
        maintenance.setMaintenanceType("日常保养");
        maintenance.setHandler("李保养");
        maintenance.setMaintenanceResult("NORMAL");
        maintenance.setStatusAfter("AVAILABLE");
        assertThrows(RuntimeException.class, () -> padMaintenanceService.register(maintenance));

        // 重复判退/再通过：拒绝
        assertThrows(RuntimeException.class, () -> arrivalService.reject(dto));
        PadArrivalPassDTO pass = passDto(batch.getId(), items, "A-01-01");
        assertThrows(RuntimeException.class, () -> arrivalService.passInspection(pass));
    }

    // ---------------- 台账回看 ----------------

    /** 台账按状态、批次号、垫板编号、经办人与到货日期区间筛选，展开行带出批内清单与当前状态 */
    @Test
    void pageList_filtersAndStatistics() {
        layer("A-01-01", 10);
        PadArrivalBatch b1 = arrivalService.register(arrivalDto("P-101", "P-102"));
        PadArrivalDTO dto2 = arrivalDto("P-201");
        dto2.setOperator("李库管");
        PadArrivalBatch b2 = arrivalService.register(dto2);
        // 回拨 b2 到货时间到上月，模拟历史批次
        b2.setArrivalTime(LocalDateTime.now().minusMonths(2));
        batchMapper.updateById(b2);

        arrivalService.passInspection(passDto(b1.getId(), b1.getItems(), "A-01-01"));

        // 状态筛选：通过 1、待检 1
        PadArrivalQueryDTO passedQuery = new PadArrivalQueryDTO();
        passedQuery.setStatus("PASSED");
        assertEquals(1, arrivalService.pageList(passedQuery).getTotal());
        PadArrivalQueryDTO pendingQuery = new PadArrivalQueryDTO();
        pendingQuery.setStatus("PENDING");
        Page<PadArrivalBatch> pendingPage = arrivalService.pageList(pendingQuery);
        assertEquals(1, pendingPage.getTotal());
        assertEquals(1, pendingPage.getRecords().get(0).getItems().size());

        // 批次号模糊筛选（完整批次号仅命中 b2）
        PadArrivalQueryDTO noQuery = new PadArrivalQueryDTO();
        noQuery.setBatchNo(b2.getBatchNo());
        Page<PadArrivalBatch> noPage = arrivalService.pageList(noQuery);
        assertEquals(1, noPage.getTotal());
        assertEquals(b2.getId(), noPage.getRecords().get(0).getId());

        // 垫板编号筛选（批内明细）
        PadArrivalQueryDTO padQuery = new PadArrivalQueryDTO();
        padQuery.setPadCode("P-201");
        Page<PadArrivalBatch> padPage = arrivalService.pageList(padQuery);
        assertEquals(1, padPage.getTotal());
        assertEquals(b2.getId(), padPage.getRecords().get(0).getId());

        // 经办人筛选
        PadArrivalQueryDTO operatorQuery = new PadArrivalQueryDTO();
        operatorQuery.setOperator("李库管");
        assertEquals(1, arrivalService.pageList(operatorQuery).getTotal());

        // 到货日期区间：近 3 天仅 b1
        PadArrivalQueryDTO dateQuery = new PadArrivalQueryDTO();
        dateQuery.setStartTime(LocalDateTime.now().minusDays(3));
        assertEquals(1, arrivalService.pageList(dateQuery).getTotal());

        // 明细当前状态对照：b1 的板已转正式并在架
        PadArrivalItem item = arrivalService.getById(b1.getId()).getItems().get(0);
        assertEquals("OFFICIAL", item.getCurrentStockStatus());
        assertEquals("A-01-01", item.getCurrentLayerCode());

        // 统计口径
        assertEquals(1L, arrivalService.statistics().get("pendingCount"));
        assertEquals(1L, arrivalService.statistics().get("passedCount"));
        assertEquals(1L, arrivalService.statistics().get("monthArrivalCount"));
    }

    private PadArrivalPassDTO.Target target(Long padId, String layerCode) {
        PadArrivalPassDTO.Target t = new PadArrivalPassDTO.Target();
        t.setPadId(padId);
        t.setLayerCode(layerCode);
        return t;
    }
}
