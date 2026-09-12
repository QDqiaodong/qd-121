package com.stamping.pad;

import com.stamping.pad.dto.PadCheckoutDTO;
import com.stamping.pad.dto.PadQueryDTO;
import com.stamping.pad.dto.PadReturnDTO;
import com.stamping.pad.entity.PadBorrowRecord;
import com.stamping.pad.entity.PadInfo;
import com.stamping.pad.entity.ShelfLayer;
import com.stamping.pad.mapper.LayerBlockRecordMapper;
import com.stamping.pad.mapper.PadBorrowRecordMapper;
import com.stamping.pad.mapper.PadInfoMapper;
import com.stamping.pad.mapper.ShelfLayerMapper;
import com.stamping.pad.service.PadBorrowService;
import com.stamping.pad.service.PadInfoService;
import com.stamping.pad.service.ShelfLayerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 货架编号筛选链路 + 领用离架/归还上架刷新验证。
 * 使用 H2(MySQL 模式) 内存库，避免依赖外部 MySQL。
 */
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ShelfFilterBorrowFlowTest {

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

    private Long padA1;
    private Long padA2;
    private Long padB1;

    @BeforeEach
    void setUp() {
        padBorrowRecordMapper.delete(null);
        blockRecordMapper.delete(null);
        padInfoMapper.delete(null);
        shelfLayerMapper.delete(null);

        // 货架 A-01 两个层位、货架 B-01 一个层位
        shelfLayerMapper.insert(layer("A-01-01", "A-01", "A区01货架第1层", 1));
        shelfLayerMapper.insert(layer("A-01-02", "A-01", "A区01货架第2层", 2));
        shelfLayerMapper.insert(layer("B-01-01", "B-01", "B区01货架第1层", 1));

        padA1 = pad("P-A001", "A-01-01");
        padA2 = pad("P-A002", "A-01-02");
        padB1 = pad("P-B001", "B-01-01");
    }

    private ShelfLayer layer(String code, String shelfCode, String name, int order) {
        ShelfLayer l = new ShelfLayer();
        l.setLayerCode(code);
        l.setShelfCode(shelfCode);
        l.setLayerName(name);
        l.setLayerOrder(order);
        return l;
    }

    private Long pad(String code, String layerCode) {
        PadInfo p = new PadInfo();
        p.setPadCode(code);
        p.setMoldType("模具-" + code);
        p.setLength(new BigDecimal("1000.00"));
        p.setWidth(new BigDecimal("800.00"));
        p.setThickness(new BigDecimal("20.00"));
        p.setShelfLayerCode(layerCode);
        p.setBindTime(LocalDateTime.now());
        p.setCreateTime(LocalDateTime.now());
        p.setUpdateTime(LocalDateTime.now());
        padInfoMapper.insert(p);
        return p.getId();
    }

    // ---------------- 筛选链路 ----------------

    /** 按货架编号筛选：货架编号、层位编码、垫板列表三者一致 */
    @Test
    void filterByShelfCode_returnsAllPadsOnShelf() {
        List<PadInfo> padsOnA = padInfoService.listByShelfCode("A-01");
        assertEquals(2, padsOnA.size());
        assertTrue(padsOnA.stream().allMatch(p -> "A-01".equals(p.getShelfCode())));
        assertTrue(padsOnA.stream().map(PadInfo::getPadCode).toList().containsAll(List.of("P-A001", "P-A002")));

        List<PadInfo> padsOnB = padInfoService.listByShelfCode("B-01");
        assertEquals(1, padsOnB.size());
        assertEquals("P-B001", padsOnB.get(0).getPadCode());
        assertEquals("B-01-01", padsOnB.get(0).getShelfLayerCode());
    }

    /** 非法/不存在货架编号：返回空列表而不是抛接口异常 */
    @Test
    void filterByShelfCode_illegalCode_returnsEmpty() {
        assertTrue(padInfoService.listByShelfCode("NOT-EXIST").isEmpty());
        assertTrue(padInfoService.listByShelfCode("").isEmpty());
        assertTrue(padInfoService.listByShelfCode("   ").isEmpty());
        assertTrue(padInfoService.listByShelfCode(null).isEmpty());
        // 超长编号（超过字段长度 64）按非法处理，不进 SQL
        assertTrue(padInfoService.listByShelfCode("X".repeat(65)).isEmpty());
    }

    /** 非法/不存在层位编码：返回空列表 */
    @Test
    void filterByLayerCode_illegalCode_returnsEmpty() {
        assertTrue(padInfoService.listByLayerCode("NO-SUCH-LAYER").isEmpty());
        assertTrue(padInfoService.listByLayerCode("  ").isEmpty());
        assertTrue(padInfoService.listByLayerCode(null).isEmpty());
    }

    /** 编号前后空白被 trim，仍能正确命中 */
    @Test
    void filterByShelfCode_trimsWhitespace() {
        assertEquals(2, padInfoService.listByShelfCode("  A-01  ").size());
        assertEquals(1, padInfoService.listByLayerCode(" B-01-01 ").size());
    }

    /** 分页查询同时带货架与层位条件，且二者匹配时结果正确；矛盾时为空 */
    @Test
    void pageQuery_shelfAndLayerConditions() {
        PadQueryDTO query = new PadQueryDTO();
        query.setShelfCode("A-01");
        query.setShelfLayerCode("A-01-01");
        assertEquals(1, padInfoService.pageList(query).getRecords().size());

        // 层位属于 B 货架但选了 A 货架：矛盾条件应为空（前端级联修复的后端语义）
        query.setShelfLayerCode("B-01-01");
        assertEquals(0, padInfoService.pageList(query).getRecords().size());

        // 仅货架条件
        PadQueryDTO shelfOnly = new PadQueryDTO();
        shelfOnly.setShelfCode("A-01");
        assertEquals(2, padInfoService.pageList(shelfOnly).getRecords().size());
    }

    /** 货架分组视图：每个层位 padCount 与其 padList 一致 */
    @Test
    void groupView_countMatchesPadList() {
        List<ShelfLayer> layers = shelfLayerService.listGroupByShelf();
        ShelfLayer a1 = layers.stream().filter(l -> "A-01-01".equals(l.getLayerCode())).findFirst().orElseThrow();
        assertEquals(1, a1.getPadCount());
        assertEquals(1, a1.getPadList().size());
        assertEquals("P-A001", a1.getPadList().get(0).getPadCode());

        ShelfLayer emptyLayer = layer("A-01-99", "A-01", "空层位", 9);
        shelfLayerMapper.insert(emptyLayer);
        List<ShelfLayer> refreshed = shelfLayerService.listGroupByShelf();
        ShelfLayer empty = refreshed.stream().filter(l -> "A-01-99".equals(l.getLayerCode())).findFirst().orElseThrow();
        assertEquals(0, empty.getPadCount());
        assertTrue(empty.getPadList().isEmpty());
    }

    // ---------------- 领用离架 / 归还上架刷新 ----------------

    /** 领用离架后：垫板离架、货架筛选与占用数同步减少 */
    @Test
    void checkout_refreshesShelfFilterAndCounts() {
        PadCheckoutDTO dto = new PadCheckoutDTO();
        dto.setPadId(padA1);
        dto.setBorrower("张三");
        dto.setProductionLine("冲压一线-1号工位");
        dto.setPurpose("换模使用");
        PadBorrowRecord record = padBorrowService.checkout(dto);
        assertNotNull(record.getId());
        assertEquals("BORROWED", record.getStatus());
        assertEquals("A-01-01", record.getOriginLayerCode());

        // 垫板已离架，层位/货架筛选均不应再返回它
        assertTrue(padInfoService.listByLayerCode("A-01-01").isEmpty());
        List<PadInfo> padsOnA = padInfoService.listByShelfCode("A-01");
        assertEquals(1, padsOnA.size());
        assertEquals("P-A002", padsOnA.get(0).getPadCode());

        // 占用数刷新：A-01-01 变为 0，A-01-02 仍为 1
        ShelfLayer a1 = shelfLayerService.getByCode("A-01-01");
        ShelfLayer a2 = shelfLayerService.getByCode("A-01-02");
        assertEquals(0, a1.getPadCount());
        assertEquals(1, a2.getPadCount());

        // 垫板档案层位置空、处于领用中
        PadInfo offShelf = padInfoMapper.selectById(padA1);
        assertNull(offShelf.getShelfLayerCode());
        assertNull(offShelf.getBindTime());

        // 分页接口仍能查到该垫板并带领用状态，但不再属于 A-01 货架
        PadQueryDTO all = new PadQueryDTO();
        all.setPageSize(100L);
        PadInfo borrowed = padInfoService.pageList(all).getRecords().stream()
                .filter(p -> p.getId().equals(padA1)).findFirst().orElseThrow();
        assertEquals("BORROWED", borrowed.getBorrowStatus());
        assertEquals(1, padInfoService.listByShelfCode("A-01").size());
    }

    /** 归还上架后：垫板重新出现在目标层位/货架，占用数恢复 */
    @Test
    void return_shelfRefreshesFilterAndCounts() {
        // 先领用 P-B001 与 P-A001：B-01-01 释放，A-01-01 也腾出一个可归还的空闲层位
        PadCheckoutDTO vacateA = new PadCheckoutDTO();
        vacateA.setPadId(padA1);
        vacateA.setBorrower("王五");
        vacateA.setProductionLine("冲压三线");
        padBorrowService.checkout(vacateA);

        PadCheckoutDTO checkoutB = new PadCheckoutDTO();
        checkoutB.setPadId(padB1);
        checkoutB.setBorrower("李四");
        checkoutB.setProductionLine("冲压二线-2号工位");
        PadBorrowRecord borrowRecord = padBorrowService.checkout(checkoutB);

        // 两个原层位均释放，占用数为 0
        assertEquals(0, shelfLayerService.getByCode("B-01-01").getPadCount());
        assertEquals(0, shelfLayerService.getByCode("A-01-01").getPadCount());
        assertTrue(padInfoService.listByShelfCode("B-01").isEmpty());

        // P-B001 归还到空闲层位 A-01-01
        PadReturnDTO ret = new PadReturnDTO();
        ret.setId(borrowRecord.getId());
        ret.setReturnLayerCode("A-01-01");
        PadBorrowRecord returned = padBorrowService.doReturn(ret);
        assertEquals("RETURNED", returned.getStatus());
        assertEquals("A-01-01", returned.getReturnLayerCode());

        // 垫板回到 A 货架 A-01-01 层
        PadInfo backOnShelf = padInfoMapper.selectById(padB1);
        assertEquals("A-01-01", backOnShelf.getShelfLayerCode());
        assertNotNull(backOnShelf.getBindTime());
        // A 货架：P-A002 仍在架 + P-B001 归还上架 = 2
        assertEquals(2, padInfoService.listByShelfCode("A-01").size());
        assertEquals(1, padInfoService.listByLayerCode("A-01-01").size());
        assertEquals("P-B001", padInfoService.listByLayerCode("A-01-01").get(0).getPadCode());
        assertEquals(1, shelfLayerService.getByCode("A-01-01").getPadCount());
    }

    /** 归还要到已占用层位时必须拒绝，占用数不变 */
    @Test
    void return_toOccupiedLayer_rejected() {
        PadCheckoutDTO checkout = new PadCheckoutDTO();
        checkout.setPadId(padB1);
        checkout.setBorrower("李四");
        checkout.setProductionLine("冲压二线");
        PadBorrowRecord rec = padBorrowService.checkout(checkout);

        // A-01-01 仍被 P-A001 占用，归还应失败
        PadReturnDTO ret = new PadReturnDTO();
        ret.setId(rec.getId());
        ret.setReturnLayerCode("A-01-01");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> padBorrowService.doReturn(ret));
        assertTrue(ex.getMessage().contains("已被占用"));

        // 数据未变：P-B001 仍离架，A-01-01 占用仍是 P-A001
        assertNull(padInfoMapper.selectById(padB1).getShelfLayerCode());
        assertEquals(1, shelfLayerService.getByCode("A-01-01").getPadCount());
        assertEquals("P-A001", padInfoService.listByLayerCode("A-01-01").get(0).getPadCode());
    }

    /** 归还要到不存在/空白层位时给出明确错误，不产生 500 */
    @Test
    void return_toIllegalLayer_rejected() {
        PadCheckoutDTO checkout = new PadCheckoutDTO();
        checkout.setPadId(padA1);
        checkout.setBorrower("赵六");
        checkout.setProductionLine("冲压一线");
        PadBorrowRecord rec = padBorrowService.checkout(checkout);

        PadReturnDTO missing = new PadReturnDTO();
        missing.setId(rec.getId());
        missing.setReturnLayerCode("NO-SUCH-LAYER");
        assertThrows(RuntimeException.class, () -> padBorrowService.doReturn(missing));

        PadReturnDTO blank = new PadReturnDTO();
        blank.setId(rec.getId());
        blank.setReturnLayerCode("   ");
        assertThrows(RuntimeException.class, () -> padBorrowService.doReturn(blank));
    }

    /** 领用后层位调整被禁止，保证在架状态与领用闭环一致 */
    @Test
    void borrowedPad_cannotRebindOrDelete() {
        PadCheckoutDTO checkout = new PadCheckoutDTO();
        checkout.setPadId(padA1);
        checkout.setBorrower("孙七");
        checkout.setProductionLine("冲压一线");
        padBorrowService.checkout(checkout);

        com.stamping.pad.dto.BindLayerDTO bind = new com.stamping.pad.dto.BindLayerDTO();
        bind.setPadId(padA1);
        bind.setLayerCode("A-01-02");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> padInfoService.bindLayer(bind));
        assertTrue(ex.getMessage().contains("领用中"));

        RuntimeException deleteEx = assertThrows(RuntimeException.class, () -> padInfoService.delete(padA1));
        assertTrue(deleteEx.getMessage().contains("领用中"));
    }
}
