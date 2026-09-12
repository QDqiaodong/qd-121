package com.stamping.pad;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.dto.PadCheckoutDTO;
import com.stamping.pad.dto.PadInfoDTO;
import com.stamping.pad.dto.PadMoldReserveDTO;
import com.stamping.pad.dto.PadMoldReserveQueryDTO;
import com.stamping.pad.dto.PadMoldReserveReleaseDTO;
import com.stamping.pad.entity.PadInfo;
import com.stamping.pad.entity.PadMoldReserveItem;
import com.stamping.pad.entity.PadMoldReserveRecord;
import com.stamping.pad.entity.ShelfLayer;
import com.stamping.pad.mapper.LayerAdjustRecordMapper;
import com.stamping.pad.mapper.PadBorrowRecordMapper;
import com.stamping.pad.mapper.PadInfoMapper;
import com.stamping.pad.mapper.PadMoldReserveItemMapper;
import com.stamping.pad.mapper.PadMoldReserveRecordMapper;
import com.stamping.pad.mapper.ShelfLayerMapper;
import com.stamping.pad.service.PadBorrowService;
import com.stamping.pad.service.PadInfoService;
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
 * 换模垫板预留链路：换模前登记模具、预留板清单、生效时段与经办人，
 * 预留期内（含待生效）禁止其他产线领用、解绑/换层与强制离架；
 * 到期自动释放、未到期可手工释放，释放后恢复可领；
 * 台账按预留状态与日期回看，刷新后领用可选板、层位占用与档案状态保持一致。
 */
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PadMoldReserveFlowTest {

    @Autowired
    private PadMoldReserveService reserveService;
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
    private PadMoldReserveRecordMapper reserveRecordMapper;
    @Autowired
    private PadMoldReserveItemMapper reserveItemMapper;
    @Autowired
    private LayerAdjustRecordMapper recordMapper;

    @BeforeEach
    void setUp() {
        padBorrowRecordMapper.delete(null);
        recordMapper.delete(null);
        reserveItemMapper.delete(null);
        reserveRecordMapper.delete(null);
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

    private Long padOnShelf(String code, String layerCode, String status) {
        PadInfo p = new PadInfo();
        p.setPadCode(code);
        p.setMoldType("模具-" + code);
        p.setShelfLayerCode(layerCode);
        p.setBindTime(LocalDateTime.now());
        p.setMaintenanceStatus(status);
        p.setCreateTime(LocalDateTime.now());
        p.setUpdateTime(LocalDateTime.now());
        padInfoMapper.insert(p);
        return p.getId();
    }

    private Long padOnShelf(String code, String layerCode) {
        return padOnShelf(code, layerCode, "AVAILABLE");
    }

    private PadMoldReserveDTO reserveDto(List<Long> padIds) {
        PadMoldReserveDTO dto = new PadMoldReserveDTO();
        dto.setMoldCode("MOLD-01");
        dto.setMoldName("前围板冲压模");
        dto.setProductionLine("冲压二线-1号工位");
        dto.setPadIds(padIds);
        dto.setStartTime(LocalDateTime.now().minusHours(1));
        dto.setEndTime(LocalDateTime.now().plusDays(2));
        dto.setOperator("王库管");
        return dto;
    }

    private PadCheckoutDTO checkoutDto(Long padId, String borrower) {
        PadCheckoutDTO dto = new PadCheckoutDTO();
        dto.setPadId(padId);
        dto.setBorrower(borrower);
        dto.setProductionLine("冲压一线");
        return dto;
    }

    // ---------------- 登记预留 ----------------

    /** 登记预留：写入模具、清单、时段与经办人，明细层位做快照，档案实时带出预留标识 */
    @Test
    void register_writesRecordItemsAndSnapshots() {
        layer("A-01-01", 10);
        Long p1 = padOnShelf("P-001", "A-01-01");
        Long p2 = padOnShelf("P-002", "A-01-01");

        PadMoldReserveRecord record = reserveService.register(reserveDto(List.of(p1, p2)));
        assertNotNull(record.getId());
        assertEquals("ACTIVE", record.getStatus());
        assertEquals("MOLD-01", record.getMoldCode());
        assertEquals(2, record.getItems() == null ? 2 : record.getItems().size());

        List<PadMoldReserveItem> items = reserveItemMapper.selectByReserveId(record.getId());
        assertEquals(2, items.size());
        assertTrue(items.stream().allMatch(i -> "A-01-01".equals(i.getLayerCode())));
        assertEquals("AVAILABLE", items.get(0).getMaintenanceStatus());

        // 档案分页实时带出预留标识，刷新后领用可选板与档案状态一致
        Page<PadInfo> page = padInfoMapper.selectPageList(new Page<>(1, 10),
                new com.stamping.pad.dto.PadQueryDTO());
        page.getRecords().forEach(pad -> {
            assertEquals(record.getId(), pad.getReserveId());
            assertEquals("MOLD-01", pad.getReserveMoldCode());
            // 预留板仍在架，层位占用不变
            assertEquals("A-01-01", pad.getShelfLayerCode());
        });
        assertEquals(2, shelfLayerService.getByCode("A-01-01").getPadCount());
    }

    /** 必填校验：模具/经办人/结束时间/垫板清单缺失均拒绝，不落任何数据 */
    @Test
    void register_invalidFields_rejected() {
        layer("A-01-01", 10);
        Long p1 = padOnShelf("P-001", "A-01-01");

        PadMoldReserveDTO noMold = reserveDto(List.of(p1));
        noMold.setMoldCode("  ");
        assertThrows(RuntimeException.class, () -> reserveService.register(noMold));

        PadMoldReserveDTO noOperator = reserveDto(List.of(p1));
        noOperator.setOperator(null);
        assertThrows(RuntimeException.class, () -> reserveService.register(noOperator));

        PadMoldReserveDTO noEnd = reserveDto(List.of(p1));
        noEnd.setEndTime(null);
        assertThrows(RuntimeException.class, () -> reserveService.register(noEnd));

        PadMoldReserveDTO noPads = reserveDto(List.of());
        assertThrows(RuntimeException.class, () -> reserveService.register(noPads));

        PadMoldReserveDTO badPeriod = reserveDto(List.of(p1));
        badPeriod.setEndTime(badPeriod.getStartTime().minusMinutes(1));
        assertThrows(RuntimeException.class, () -> reserveService.register(badPeriod));

        PadMoldReserveDTO expired = reserveDto(List.of(p1));
        expired.setStartTime(LocalDateTime.now().minusDays(5));
        expired.setEndTime(LocalDateTime.now().minusMinutes(1));
        assertThrows(RuntimeException.class, () -> reserveService.register(expired));

        assertEquals(0, reserveRecordMapper.selectCount(null));
        assertEquals(0, reserveItemMapper.selectCount(null));
    }

    /** 仅在架可用垫板可预留：待检/停用/已报废/未在架/领用离架均拒绝 */
    @Test
    void register_onlyAvailableOnShelfPads() {
        layer("A-01-01", 10);
        layer("A-01-02", 10);
        Long pending = padOnShelf("P-PENDING", "A-01-01", "PENDING");
        Long disabled = padOnShelf("P-DISABLED", "A-01-01", "DISABLED");
        Long scrapped = padOnShelf("P-SCRAPPED", "A-01-01", "SCRAPPED");
        Long offShelf = padOnShelf("P-OFF", null);

        assertThrows(RuntimeException.class, () -> reserveService.register(reserveDto(List.of(pending))));
        assertThrows(RuntimeException.class, () -> reserveService.register(reserveDto(List.of(disabled))));
        assertThrows(RuntimeException.class, () -> reserveService.register(reserveDto(List.of(scrapped))));
        assertThrows(RuntimeException.class, () -> reserveService.register(reserveDto(List.of(offShelf))));

        // 领用离架中的垫板不可预留
        Long borrowed = padOnShelf("P-BORROW", "A-01-02");
        padBorrowService.checkout(checkoutDto(borrowed, "张三"));
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> reserveService.register(reserveDto(List.of(borrowed))));
        assertTrue(ex.getMessage().contains("领用离架"));

        assertEquals(0, reserveRecordMapper.selectCount(null));
    }

    /**
     * 同一垫板时段重叠的未释放预留不可重复登记；释放后再登记允许。
     * 另外演示锁板规则：上一条未到期时，即便新时段不重叠也会被"登记即锁板"拦截，
     * 须先释放（或等其到期）后才能再登记。
     */
    @Test
    void register_overlappingPad_rejected() {
        layer("A-01-01", 10);
        Long p1 = padOnShelf("P-001", "A-01-01");
        reserveService.register(reserveDto(List.of(p1)));

        // 时段重叠：拒绝并提示垫板编号
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> reserveService.register(reserveDto(List.of(p1))));
        assertTrue(ex.getMessage().contains("P-001"));
        assertEquals(1, reserveRecordMapper.selectCount(null));

        // 上一条未到期（锁板中）：即使新时段完全不重叠，也拒绝再次登记
        PadMoldReserveDTO later = reserveDto(List.of(p1));
        later.setStartTime(LocalDateTime.now().plusDays(5));
        later.setEndTime(LocalDateTime.now().plusDays(7));
        assertThrows(RuntimeException.class, () -> reserveService.register(later));

        // 手工释放后可再登记（开始时间在未来 -> 待生效）
        Long firstId = reserveRecordMapper.selectList(null).get(0).getId();
        PadMoldReserveReleaseDTO release = new PadMoldReserveReleaseDTO();
        release.setId(firstId);
        release.setReleaseConclusion("换模计划调整");
        reserveService.release(release);
        PadMoldReserveRecord second = reserveService.register(later);
        assertEquals("PENDING", second.getStatus());
    }

    // ---------------- 预留期内约束 ----------------

    /** 预留期内（生效中）禁止其他产线领用、解绑、换层与编辑换层；非层位编辑不受影响 */
    @Test
    void activeReserve_blocksCheckoutUnbindRebindAndEditLayer() {
        layer("A-01-01", 10);
        layer("A-01-02", 10);
        Long p1 = padOnShelf("P-001", "A-01-01");
        reserveService.register(reserveDto(List.of(p1)));

        // 其他产线领用：拒绝
        RuntimeException checkoutEx = assertThrows(RuntimeException.class,
                () -> padBorrowService.checkout(checkoutDto(p1, "李四")));
        assertTrue(checkoutEx.getMessage().contains("预留"));
        assertEquals("A-01-01", padInfoMapper.selectById(p1).getShelfLayerCode());

        // 解绑：拒绝
        com.stamping.pad.dto.UnbindLayerDTO unbind = new com.stamping.pad.dto.UnbindLayerDTO();
        unbind.setPadId(p1);
        assertThrows(RuntimeException.class, () -> padInfoService.unbindLayer(unbind));

        // 换绑到其他层：拒绝
        com.stamping.pad.dto.BindLayerDTO rebind = new com.stamping.pad.dto.BindLayerDTO();
        rebind.setPadId(p1);
        rebind.setLayerCode("A-01-02");
        assertThrows(RuntimeException.class, () -> padInfoService.bindLayer(rebind));

        // 档案编辑换层/清空层位：拒绝
        PadInfoDTO edit = new PadInfoDTO();
        PadInfo origin = padInfoMapper.selectById(p1);
        edit.setId(p1);
        edit.setPadCode(origin.getPadCode());
        edit.setShelfLayerCode("A-01-02");
        assertThrows(RuntimeException.class, () -> padInfoService.update(edit));
        PadInfoDTO clear = new PadInfoDTO();
        clear.setId(p1);
        clear.setPadCode(origin.getPadCode());
        clear.setShelfLayerCode(null);
        assertThrows(RuntimeException.class, () -> padInfoService.update(clear));
        assertEquals("A-01-01", padInfoMapper.selectById(p1).getShelfLayerCode());

        // 保留原层位的普通编辑（改备注）：允许
        edit.setShelfLayerCode("A-01-01");
        edit.setRemark("换模预留中");
        padInfoService.update(edit);
        assertEquals("换模预留中", padInfoMapper.selectById(p1).getRemark());

        // 删除：拒绝
        assertThrows(RuntimeException.class, () -> padInfoService.delete(p1));
    }

    /** 待生效预留同样锁板：生效开始时间未到也不能被其他产线领用 */
    @Test
    void pendingReserve_blocksCheckoutBeforeStartTime() {
        layer("A-01-01", 10);
        Long p1 = padOnShelf("P-001", "A-01-01");
        PadMoldReserveDTO dto = reserveDto(List.of(p1));
        dto.setStartTime(LocalDateTime.now().plusDays(1));
        dto.setEndTime(LocalDateTime.now().plusDays(3));
        PadMoldReserveRecord pending = reserveService.register(dto);
        assertEquals("PENDING", pending.getStatus());

        // 档案已带出预留标识（selectByIds 走联表分页列，含预留子查询），领用被拒
        assertEquals(pending.getId(), padInfoMapper.selectByIds(List.of(p1)).get(0).getReserveId());
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> padBorrowService.checkout(checkoutDto(p1, "李四")));
        assertTrue(ex.getMessage().contains("MOLD-01"));
    }

    // ---------------- 到期 / 手工释放 ----------------

    /** 到期自动释放：结束时间过后领用立即放行，台账刷新落为已到期并补写系统释放信息 */
    @Test
    void expiredReserve_releasesAutomatically() {
        layer("A-01-01", 10);
        Long p1 = padOnShelf("P-001", "A-01-01");
        PadMoldReserveRecord record = reserveService.register(reserveDto(List.of(p1)));

        // 拨钟：结束时间已过
        record.setEndTime(LocalDateTime.now().minusMinutes(1));
        reserveRecordMapper.updateById(record);

        // 约束按结束时间实时判断：不刷新状态也已放行领用
        padBorrowService.checkout(checkoutDto(p1, "换模工位"));
        assertNull(padInfoMapper.selectById(p1).getShelfLayerCode());

        // 台账刷新后状态落为已到期，系统补写释放信息
        reserveService.refreshStatus();
        PadMoldReserveRecord expired = reserveRecordMapper.selectById(record.getId());
        assertEquals("EXPIRED", expired.getStatus());
        assertEquals(expired.getEndTime(), expired.getReleaseTime());
        assertNotNull(expired.getReleaseConclusion());
        assertEquals("系统", expired.getReleaseOperator());
    }

    /** 手工释放必须填写结论；释放后立即恢复可领、可解绑，且禁止重复释放 */
    @Test
    void manualRelease_requiresConclusionAndRestoresPads() {
        layer("A-01-01", 10);
        layer("A-01-02", 10);
        Long p1 = padOnShelf("P-001", "A-01-01");
        PadMoldReserveRecord record = reserveService.register(reserveDto(List.of(p1)));

        // 空结论：拒绝
        PadMoldReserveReleaseDTO blank = new PadMoldReserveReleaseDTO();
        blank.setId(record.getId());
        blank.setReleaseConclusion(" ");
        assertThrows(RuntimeException.class, () -> reserveService.release(blank));
        assertEquals("ACTIVE", reserveRecordMapper.selectById(record.getId()).getStatus());

        // 填写结论：释放成功，缺省经办人取登记经办人
        PadMoldReserveReleaseDTO release = new PadMoldReserveReleaseDTO();
        release.setId(record.getId());
        release.setReleaseConclusion("换模完成，板已上线使用");
        PadMoldReserveRecord released = reserveService.release(release);
        assertEquals("RELEASED", released.getStatus());
        assertEquals("王库管", released.getReleaseOperator());
        assertNotNull(released.getReleaseTime());

        // 重复释放：拒绝
        assertThrows(RuntimeException.class,
                () -> reserveService.release(release));

        // 释放后恢复可领、可换层
        padBorrowService.checkout(checkoutDto(p1, "其他产线"));
        assertNull(padInfoMapper.selectById(p1).getShelfLayerCode());
    }

    /** 释放时间不能早于预留开始时间 */
    @Test
    void release_beforeStartTime_rejected() {
        layer("A-01-01", 10);
        Long p1 = padOnShelf("P-001", "A-01-01");
        PadMoldReserveRecord record = reserveService.register(reserveDto(List.of(p1)));

        PadMoldReserveReleaseDTO release = new PadMoldReserveReleaseDTO();
        release.setId(record.getId());
        release.setReleaseConclusion("提前释放");
        release.setReleaseTime(LocalDateTime.now().minusDays(10));
        assertThrows(RuntimeException.class, () -> reserveService.release(release));
        assertEquals("ACTIVE", reserveRecordMapper.selectById(record.getId()).getStatus());
    }

    // ---------------- 台账回看 ----------------

    /** 台账按预留状态、模具、垫板编号与日期区间筛选，展开行带出预留板清单与当前状态 */
    @Test
    void pageList_filtersByStatusMoldPadAndDateRange() {
        layer("A-01-01", 10);
        layer("A-01-02", 10);
        Long p1 = padOnShelf("P-001", "A-01-01");
        Long p2 = padOnShelf("P-002", "A-01-02");
        PadMoldReserveRecord r1 = reserveService.register(reserveDto(List.of(p1)));

        PadMoldReserveDTO dto2 = reserveDto(List.of(p2));
        dto2.setMoldCode("MOLD-02");
        PadMoldReserveRecord r2 = reserveService.register(dto2);
        // 回拨到历史时段（已过期的预留不能直接登记），模拟一条历史到期单
        r2.setStartTime(LocalDateTime.now().minusDays(20));
        r2.setEndTime(LocalDateTime.now().minusDays(10));
        reserveRecordMapper.updateById(r2);

        // 生效中 1 条（r1），已到期 1 条（惰性刷新落状态）
        PadMoldReserveQueryDTO activeQuery = new PadMoldReserveQueryDTO();
        activeQuery.setStatus("ACTIVE");
        Page<PadMoldReserveRecord> activePage = reserveService.pageList(activeQuery);
        assertEquals(1, activePage.getTotal());
        assertEquals(1, activePage.getRecords().get(0).getItems().size());

        PadMoldReserveQueryDTO expiredQuery = new PadMoldReserveQueryDTO();
        expiredQuery.setStatus("EXPIRED");
        assertEquals(1, reserveService.pageList(expiredQuery).getTotal());

        // 按模具编码筛选
        PadMoldReserveQueryDTO moldQuery = new PadMoldReserveQueryDTO();
        moldQuery.setMoldCode("MOLD-02");
        Page<PadMoldReserveRecord> moldPage = reserveService.pageList(moldQuery);
        assertEquals(1, moldPage.getTotal());
        assertEquals("MOLD-02", moldPage.getRecords().get(0).getMoldCode());

        // 按垫板编号筛选
        PadMoldReserveQueryDTO padQuery = new PadMoldReserveQueryDTO();
        padQuery.setPadCode("P-001");
        assertEquals(1, reserveService.pageList(padQuery).getTotal());

        // 按预留开始日期区间筛选：近 3 天内仅 r1
        PadMoldReserveQueryDTO dateQuery = new PadMoldReserveQueryDTO();
        dateQuery.setStartTime(LocalDateTime.now().minusDays(3));
        assertEquals(1, reserveService.pageList(dateQuery).getTotal());

        // 明细当前状态：r1 的板仍在 A-01-01
        PadMoldReserveItem item = reserveService.getById(r1.getId()).getItems().get(0);
        assertEquals("P-001", item.getPadCode());
        assertEquals("A-01-01", item.getCurrentLayerCode());

        // 统计口径
        assertEquals(1L, reserveService.statistics().get("activeCount"));
        assertEquals(1L, reserveService.statistics().get("expiredCount"));
        assertEquals(0L, reserveService.statistics().get("releasedCount"));
    }
}
