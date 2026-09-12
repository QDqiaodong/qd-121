package com.stamping.pad;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.dto.BindLayerDTO;
import com.stamping.pad.dto.LayerCapacityExpandDTO;
import com.stamping.pad.dto.LayerCapacityExpandFinishDTO;
import com.stamping.pad.dto.LayerCapacityExpandQueryDTO;
import com.stamping.pad.dto.PadCheckoutDTO;
import com.stamping.pad.dto.PadInfoDTO;
import com.stamping.pad.dto.PadReturnDTO;
import com.stamping.pad.entity.LayerCapacityExpandRecord;
import com.stamping.pad.entity.PadBorrowRecord;
import com.stamping.pad.entity.PadInfo;
import com.stamping.pad.entity.ShelfLayer;
import com.stamping.pad.mapper.LayerAdjustRecordMapper;
import com.stamping.pad.mapper.LayerBlockRecordMapper;
import com.stamping.pad.mapper.LayerCapacityExpandRecordMapper;
import com.stamping.pad.mapper.PadBorrowRecordMapper;
import com.stamping.pad.mapper.PadInfoMapper;
import com.stamping.pad.mapper.ShelfLayerMapper;
import com.stamping.pad.service.LayerCapacityExpandService;
import com.stamping.pad.service.PadBorrowService;
import com.stamping.pad.service.PadInfoService;
import com.stamping.pad.service.ShelfLayerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 层位临时扩容链路：旺季到货登记扩容（原因、新配额、生效时段、经办人）后，
 * 扩容期内绑定/换绑/建档/归还上架/导入按新配额校验；到期自动回到原配额，
 * 未到期可提前结束并写结论；台账按生效状态与日期回看，
 * 刷新后层位占用进度、可选范围与档案绑定保持一致。
 */
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LayerCapacityExpandFlowTest {

    @Autowired
    private LayerCapacityExpandService expandService;
    @Autowired
    private PadInfoService padInfoService;
    @Autowired
    private ShelfLayerService shelfLayerService;
    @Autowired
    private PadBorrowService padBorrowService;
    @Autowired
    private ShelfLayerMapper shelfLayerMapper;
    @Autowired
    private PadInfoMapper padInfoMapper;
    @Autowired
    private PadBorrowRecordMapper padBorrowRecordMapper;
    @Autowired
    private LayerBlockRecordMapper blockRecordMapper;
    @Autowired
    private LayerCapacityExpandRecordMapper expandRecordMapper;
    @Autowired
    private LayerAdjustRecordMapper recordMapper;

    @BeforeEach
    void setUp() {
        padBorrowRecordMapper.delete(null);
        recordMapper.delete(null);
        blockRecordMapper.delete(null);
        expandRecordMapper.delete(null);
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

    private LayerCapacityExpandDTO expandDto(String layerCode, int expandCapacity) {
        LayerCapacityExpandDTO dto = new LayerCapacityExpandDTO();
        dto.setLayerCode(layerCode);
        dto.setExpandReason("旺季集中到货，临时加大层位配额");
        dto.setExpandCapacity(expandCapacity);
        dto.setStartTime(LocalDateTime.now().minusHours(1));
        dto.setEndTime(LocalDateTime.now().plusDays(7));
        dto.setOperator("王库管");
        return dto;
    }

    private LayerCapacityExpandFinishDTO finishDto(Long id, String conclusion) {
        LayerCapacityExpandFinishDTO dto = new LayerCapacityExpandFinishDTO();
        dto.setId(id);
        dto.setFinishConclusion(conclusion);
        return dto;
    }

    private BindLayerDTO bindDto(Long padId, String layerCode) {
        BindLayerDTO dto = new BindLayerDTO();
        dto.setPadId(padId);
        dto.setLayerCode(layerCode);
        return dto;
    }

    // ---------------- 登记扩容 ----------------

    /** 登记扩容：写入原因、新配额、生效时段与经办人，层位列表同步带出“扩容中”与实际配额 */
    @Test
    void register_writesRecordAndLayerShowsExpanded() {
        layer("A-01-01", 5);
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now().plusDays(7);

        LayerCapacityExpandDTO dto = expandDto("A-01-01", 12);
        dto.setStartTime(start);
        dto.setEndTime(end);
        LayerCapacityExpandRecord record = expandService.register(dto);

        assertNotNull(record.getId());
        assertEquals("ACTIVE", record.getStatus());
        assertEquals("旺季集中到货，临时加大层位配额", record.getExpandReason());
        assertEquals(5, record.getOriginalCapacity());
        assertEquals(12, record.getExpandCapacity());
        assertEquals(start, record.getStartTime());
        assertEquals(end, record.getEndTime());
        assertEquals("王库管", record.getOperator());

        // 层位列表/单查均带出扩容信息与实际配额，前端据此展示占用进度与可选范围
        ShelfLayer layer = shelfLayerService.getByCode("A-01-01");
        assertNotNull(layer.getActiveExpand());
        assertEquals(12, layer.getEffectiveCapacity());
        assertEquals(5, layer.getCapacity());
        List<ShelfLayer> all = shelfLayerService.listAll();
        assertNotNull(all.get(0).getActiveExpand());
        assertEquals(12, all.get(0).getEffectiveCapacity());
    }

    /** 必填与数值校验：原因/经办人/结束时间缺失、新配额不大于原配额、时段倒置均拒绝 */
    @Test
    void register_invalidFields_rejected() {
        layer("A-01-01", 5);

        LayerCapacityExpandDTO noReason = expandDto("A-01-01", 10);
        noReason.setExpandReason(" ");
        assertThrows(RuntimeException.class, () -> expandService.register(noReason));

        LayerCapacityExpandDTO noOperator = expandDto("A-01-01", 10);
        noOperator.setOperator(null);
        assertThrows(RuntimeException.class, () -> expandService.register(noOperator));

        LayerCapacityExpandDTO noEnd = expandDto("A-01-01", 10);
        noEnd.setEndTime(null);
        assertThrows(RuntimeException.class, () -> expandService.register(noEnd));

        // 新配额必须大于原配额：等于或小于均拒绝
        LayerCapacityExpandDTO equalCap = expandDto("A-01-01", 5);
        assertThrows(RuntimeException.class, () -> expandService.register(equalCap));
        LayerCapacityExpandDTO lessCap = expandDto("A-01-01", 3);
        assertThrows(RuntimeException.class, () -> expandService.register(lessCap));

        // 结束时间必须晚于开始时间
        LayerCapacityExpandDTO badPeriod = expandDto("A-01-01", 10);
        badPeriod.setEndTime(badPeriod.getStartTime().minusMinutes(1));
        assertThrows(RuntimeException.class, () -> expandService.register(badPeriod));

        LayerCapacityExpandDTO noLayer = expandDto("B-99-99", 10);
        assertThrows(RuntimeException.class, () -> expandService.register(noLayer));

        assertEquals(0, expandRecordMapper.selectCount(null));
    }

    /** 同一层位已存在生效中（含待生效）扩容时禁止重复登记 */
    @Test
    void register_duplicateActiveExpand_rejected() {
        layer("A-01-01", 5);
        expandService.register(expandDto("A-01-01", 10));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> expandService.register(expandDto("A-01-01", 20)));
        assertTrue(ex.getMessage().contains("扩容"));
        assertEquals(1, expandRecordMapper.selectCount(null));

        // 待生效（开始时间在未来）同样拦截重复登记
        layer("A-01-02", 5);
        LayerCapacityExpandDTO future = expandDto("A-01-02", 10);
        future.setStartTime(LocalDateTime.now().plusDays(1));
        future.setEndTime(LocalDateTime.now().plusDays(8));
        LayerCapacityExpandRecord pending = expandService.register(future);
        assertEquals("PENDING", pending.getStatus());
        assertThrows(RuntimeException.class, () -> expandService.register(expandDto("A-01-02", 20)));
    }

    // ---------------- 扩容期内按新配额校验 ----------------

    /** 扩容期内绑定/换绑按新配额放行：超过原配额但不超新配额可上架 */
    @Test
    void activeExpand_bindAndRebind_useExpandedCapacity() {
        layer("A-01-01", 2);
        layer("A-01-02", 5);
        padOnShelf("P-001", "A-01-01");
        padOnShelf("P-002", "A-01-01");
        Long freePad = padOnShelf("P-003", null);
        Long movingPad = padOnShelf("P-004", "A-01-02");

        // 未扩容：A-01-01 已满（2/2），绑定被拒
        assertThrows(RuntimeException.class,
                () -> padInfoService.bindLayer(bindDto(freePad, "A-01-01")));

        // 扩容到 4：绑定第 3 块、换绑第 4 块均按新配额放行
        expandService.register(expandDto("A-01-01", 4));
        padInfoService.bindLayer(bindDto(freePad, "A-01-01"));
        padInfoService.bindLayer(bindDto(movingPad, "A-01-01"));
        assertEquals(4, shelfLayerService.getByCode("A-01-01").getPadCount());

        // 达到新配额（4/4）后再次上架仍被拒
        Long extraPad = padOnShelf("P-005", null);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> padInfoService.bindLayer(bindDto(extraPad, "A-01-01")));
        assertTrue(ex.getMessage().contains("已满"));
        assertTrue(ex.getMessage().contains("4/4"));
    }

    /** 扩容期内建档上架与导入按新配额校验 */
    @Test
    void activeExpand_createAndImport_useExpandedCapacity() {
        layer("A-01-01", 1);
        padOnShelf("P-001", "A-01-01");
        expandService.register(expandDto("A-01-01", 3));

        // 建档带层位：原配额 1 已满，扩容后可再上架
        PadInfoDTO create = new PadInfoDTO();
        create.setPadCode("P-002");
        create.setShelfLayerCode("A-01-01");
        padInfoService.create(create);

        // 导入：同样按新配额占位
        PadInfo row = new PadInfo();
        row.setPadCode("IMP-001");
        row.setShelfLayerCode("A-01-01");
        padInfoService.importRow(row);

        assertEquals(3, shelfLayerService.getByCode("A-01-01").getPadCount());

        // 超出新配额的导入行失败，已落库数据不受影响
        PadInfo overflow = new PadInfo();
        overflow.setPadCode("IMP-002");
        overflow.setShelfLayerCode("A-01-01");
        assertThrows(RuntimeException.class, () -> padInfoService.importRow(overflow));
        assertEquals(3, padInfoMapper.selectCount(null));
    }

    /** 扩容期内归还上架按新配额校验 */
    @Test
    void activeExpand_return_usesExpandedCapacity() {
        layer("A-01-01", 1);
        layer("B-01-01", 5);
        padOnShelf("P-001", "A-01-01");
        Long borrowedPad = padOnShelf("P-002", "B-01-01");

        PadCheckoutDTO checkout = new PadCheckoutDTO();
        checkout.setPadId(borrowedPad);
        checkout.setBorrower("张三");
        checkout.setProductionLine("冲压一线");
        PadBorrowRecord record = padBorrowService.checkout(checkout);

        // 归还目标 A-01-01 已满（1/1）：拒绝
        PadReturnDTO ret = new PadReturnDTO();
        ret.setId(record.getId());
        ret.setReturnLayerCode("A-01-01");
        assertThrows(RuntimeException.class, () -> padBorrowService.doReturn(ret));

        // 扩容后：容量校验按新配额放行（占用校验要求层位完全空闲，先将领用原垫板移出）
        expandService.register(expandDto("A-01-01", 2));
        PadInfo occupying = padInfoMapper.selectList(
                new LambdaQueryWrapper<PadInfo>().eq(PadInfo::getShelfLayerCode, "A-01-01")).get(0);
        PadCheckoutDTO checkoutOccupying = new PadCheckoutDTO();
        checkoutOccupying.setPadId(occupying.getId());
        checkoutOccupying.setBorrower("李四");
        checkoutOccupying.setProductionLine("冲压二线");
        padBorrowService.checkout(checkoutOccupying);
        padBorrowService.doReturn(ret);

        assertEquals("RETURNED", padBorrowRecordMapper.selectById(record.getId()).getStatus());
        assertEquals("A-01-01", padInfoMapper.selectById(borrowedPad).getShelfLayerCode());
    }

    /** 待生效的扩容不参与校验：开始时间未到仍按原配额 */
    @Test
    void pendingExpand_stillUsesOriginalCapacity() {
        layer("A-01-01", 1);
        padOnShelf("P-001", "A-01-01");
        Long freePad = padOnShelf("P-002", null);

        LayerCapacityExpandDTO dto = expandDto("A-01-01", 5);
        dto.setStartTime(LocalDateTime.now().plusDays(1));
        dto.setEndTime(LocalDateTime.now().plusDays(8));
        expandService.register(dto);

        // 层位列表不带出生效中扩容，实际配额仍为原配额
        ShelfLayer layer = shelfLayerService.getByCode("A-01-01");
        assertNull(layer.getActiveExpand());
        assertEquals(1, layer.getEffectiveCapacity());
        assertThrows(RuntimeException.class,
                () -> padInfoService.bindLayer(bindDto(freePad, "A-01-01")));
    }

    // ---------------- 到期自动回到原配额 ----------------

    /** 到期后自动回到原配额：校验、层位展示同步恢复，台账状态刷新为已到期 */
    @Test
    void expiredExpand_revertsToOriginalCapacity() {
        layer("A-01-01", 1);
        padOnShelf("P-001", "A-01-01");
        padOnShelf("P-002", "A-01-01");
        Long freePad = padOnShelf("P-003", null);

        // 登记时已过期（结束时间在过去）：登记即生效中，校验按新配额放行第 3 块
        LayerCapacityExpandDTO dto = expandDto("A-01-01", 3);
        dto.setStartTime(LocalDateTime.now().minusDays(7));
        dto.setEndTime(LocalDateTime.now().plusDays(1));
        expandService.register(dto);
        padInfoService.bindLayer(bindDto(freePad, "A-01-01"));
        assertEquals(3, shelfLayerService.getByCode("A-01-01").getPadCount());

        // 手动把结束时间拨到过去，模拟旺季结束到期
        LayerCapacityExpandRecord record = expandRecordMapper.selectList(null).get(0);
        record.setEndTime(LocalDateTime.now().minusMinutes(1));
        expandRecordMapper.updateById(record);

        // 容量校验立即回到原配额：在架 3 块已超原配额 1，新上架被拒
        Long extraPad = padOnShelf("P-004", null);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> padInfoService.bindLayer(bindDto(extraPad, "A-01-01")));
        assertTrue(ex.getMessage().contains("已满"));

        // 台账刷新后状态落为已到期，系统补写结束信息；层位不再带扩容标识
        expandService.refreshStatus();
        LayerCapacityExpandRecord expired = expandRecordMapper.selectById(record.getId());
        assertEquals("EXPIRED", expired.getStatus());
        assertEquals(expired.getEndTime(), expired.getFinishTime());
        assertNotNull(expired.getFinishConclusion());
        ShelfLayer layer = shelfLayerService.getByCode("A-01-01");
        assertNull(layer.getActiveExpand());
        assertEquals(1, layer.getEffectiveCapacity());
        // 在架垫板保留不强制离架，档案绑定保持一致
        assertEquals(3, layer.getPadCount());
    }

    // ---------------- 提前结束 ----------------

    /** 提前结束必须填写结论；结束后立即回到原配额，且禁止重复结束 */
    @Test
    void finish_requiresConclusion_andRevertsCapacity() {
        layer("A-01-01", 1);
        padOnShelf("P-001", "A-01-01");
        LayerCapacityExpandRecord record = expandService.register(expandDto("A-01-01", 4));

        // 扩容中可再上架
        Long freePad = padOnShelf("P-002", null);
        padInfoService.bindLayer(bindDto(freePad, "A-01-01"));
        assertEquals(2, shelfLayerService.getByCode("A-01-01").getPadCount());

        // 空结论：拒绝
        assertThrows(RuntimeException.class,
                () -> expandService.finish(finishDto(record.getId(), "  ")));
        assertEquals("ACTIVE", expandRecordMapper.selectById(record.getId()).getStatus());

        // 填写结论：结束成功，记录结束时间/结论/经办人
        LayerCapacityExpandFinishDTO finish = finishDto(record.getId(), "旺季到货结束，恢复原配额");
        finish.setFinishOperator("赵班长");
        LayerCapacityExpandRecord ended = expandService.finish(finish);
        assertEquals("ENDED", ended.getStatus());
        assertEquals("旺季到货结束，恢复原配额", ended.getFinishConclusion());
        assertEquals("赵班长", ended.getFinishOperator());
        assertNotNull(ended.getFinishTime());

        // 重复结束：拒绝
        assertThrows(RuntimeException.class,
                () -> expandService.finish(finishDto(record.getId(), "再次结束")));

        // 结束后立即回到原配额：在架 2 块已超原配额 1，新上架被拒，层位不再带扩容标识
        ShelfLayer layer = shelfLayerService.getByCode("A-01-01");
        assertNull(layer.getActiveExpand());
        assertEquals(1, layer.getEffectiveCapacity());
        Long extraPad = padOnShelf("P-003", null);
        assertThrows(RuntimeException.class,
                () -> padInfoService.bindLayer(bindDto(extraPad, "A-01-01")));
    }

    /** 结束时间不能早于生效开始时间 */
    @Test
    void finish_beforeStartTime_rejected() {
        layer("A-01-01", 5);
        LayerCapacityExpandDTO dto = expandDto("A-01-01", 10);
        dto.setStartTime(LocalDateTime.now().minusHours(2));
        LayerCapacityExpandRecord record = expandService.register(dto);

        LayerCapacityExpandFinishDTO finish = finishDto(record.getId(), "提前结束");
        finish.setFinishTime(LocalDateTime.now().minusHours(5));
        assertThrows(RuntimeException.class, () -> expandService.finish(finish));
        assertEquals("ACTIVE", expandRecordMapper.selectById(record.getId()).getStatus());
    }

    /** 扩容中层位（无在架垫板时）禁止删除，避免扩容台账留下无主记录 */
    @Test
    void activeExpand_deleteLayer_rejected() {
        ShelfLayer layer = layer("A-01-01", 5);
        expandService.register(expandDto("A-01-01", 10));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> shelfLayerService.delete(layer.getId()));
        assertTrue(ex.getMessage().contains("扩容"));
        assertNotNull(shelfLayerMapper.selectById(layer.getId()));
    }

    // ---------------- 台账回看 ----------------

    /** 台账按生效状态与开始日期区间筛选，统计卡片与列表口径一致 */
    @Test
    void pageList_filtersByStatusAndDateRange() {
        layer("A-01-01", 5);
        layer("A-01-02", 5);
        layer("B-01-01", 5);

        LayerCapacityExpandDTO e1 = expandDto("A-01-01", 10);
        e1.setStartTime(LocalDateTime.now().minusDays(10));
        e1.setEndTime(LocalDateTime.now().plusDays(1));
        expandService.register(e1);

        LayerCapacityExpandDTO e2 = expandDto("A-01-02", 10);
        e2.setStartTime(LocalDateTime.now().minusDays(2));
        e2.setEndTime(LocalDateTime.now().plusDays(2));
        expandService.register(e2);

        LayerCapacityExpandDTO e3 = expandDto("B-01-01", 10);
        expandService.register(e3);
        expandService.finish(finishDto(
                expandRecordMapper.selectList(new LambdaQueryWrapper<LayerCapacityExpandRecord>()
                                .eq(LayerCapacityExpandRecord::getLayerCode, "B-01-01"))
                        .get(0).getId(),
                "到货结束，提前恢复原配额"));

        // 按状态筛选：生效中 2 条，已结束 1 条
        LayerCapacityExpandQueryDTO activeQuery = new LayerCapacityExpandQueryDTO();
        activeQuery.setStatus("ACTIVE");
        Page<LayerCapacityExpandRecord> activePage = expandService.pageList(activeQuery);
        assertEquals(2, activePage.getTotal());

        LayerCapacityExpandQueryDTO endedQuery = new LayerCapacityExpandQueryDTO();
        endedQuery.setStatus("ENDED");
        Page<LayerCapacityExpandRecord> endedPage = expandService.pageList(endedQuery);
        assertEquals(1, endedPage.getTotal());
        assertEquals("到货结束，提前恢复原配额", endedPage.getRecords().get(0).getFinishConclusion());

        // 按日期区间筛选：最近 3 天内 2 条
        LayerCapacityExpandQueryDTO dateQuery = new LayerCapacityExpandQueryDTO();
        dateQuery.setStartTime(LocalDateTime.now().minusDays(3));
        Page<LayerCapacityExpandRecord> datePage = expandService.pageList(dateQuery);
        assertEquals(2, datePage.getTotal());

        // 按层位编码筛选
        LayerCapacityExpandQueryDTO layerQuery = new LayerCapacityExpandQueryDTO();
        layerQuery.setLayerCode("A-01-02");
        Page<LayerCapacityExpandRecord> layerPage = expandService.pageList(layerQuery);
        assertEquals(1, layerPage.getTotal());
        assertEquals("A-01-02", layerPage.getRecords().get(0).getLayerCode());
        assertEquals("A-01-02层", layerPage.getRecords().get(0).getLayerName());

        // 统计：生效中/待生效/已结束/本月
        assertEquals(2L, expandService.statistics().get("activeCount"));
        assertEquals(0L, expandService.statistics().get("pendingCount"));
        assertEquals(1L, expandService.statistics().get("endedCount"));
        assertEquals(3L, expandService.statistics().get("monthExpandCount"));

        // 刷新后层位占用进度、可选范围与档案绑定保持一致
        ShelfLayer expanded = shelfLayerService.getByCode("A-01-01");
        assertNotNull(expanded.getActiveExpand());
        assertEquals(10, expanded.getEffectiveCapacity());
        ShelfLayer ended = shelfLayerService.getByCode("B-01-01");
        assertNull(ended.getActiveExpand());
        assertEquals(5, ended.getEffectiveCapacity());
    }
}
