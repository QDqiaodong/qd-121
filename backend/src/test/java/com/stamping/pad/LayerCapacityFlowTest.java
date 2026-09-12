package com.stamping.pad;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stamping.pad.dto.BindLayerDTO;
import com.stamping.pad.dto.PadCheckoutDTO;
import com.stamping.pad.dto.PadInfoDTO;
import com.stamping.pad.dto.PadReturnDTO;
import com.stamping.pad.entity.LayerAdjustRecord;
import com.stamping.pad.entity.PadBorrowRecord;
import com.stamping.pad.entity.PadInfo;
import com.stamping.pad.entity.ShelfLayer;
import com.stamping.pad.mapper.LayerAdjustRecordMapper;
import com.stamping.pad.mapper.LayerBlockRecordMapper;
import com.stamping.pad.mapper.LayerCapacityExpandRecordMapper;
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
 * 货架分层容量配额链路：绑定/换绑/建档/归还/导入不得超配额，
 * 下调配额不得低于当前在架数，已满层位不可作为归还目标，
 * 操作后档案绑定、层位占用与调整记录保持一致。
 */
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LayerCapacityFlowTest {

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
    private LayerAdjustRecordMapper recordMapper;
    @Autowired
    private LayerBlockRecordMapper blockRecordMapper;
    @Autowired
    private LayerCapacityExpandRecordMapper expandRecordMapper;

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
        p.setLength(new BigDecimal("1000.00"));
        p.setWidth(new BigDecimal("800.00"));
        p.setThickness(new BigDecimal("20.00"));
        p.setShelfLayerCode(layerCode);
        p.setBindTime(LocalDateTime.now());
        p.setMaintenanceStatus("AVAILABLE");
        p.setCreateTime(LocalDateTime.now());
        p.setUpdateTime(LocalDateTime.now());
        padInfoMapper.insert(p);
        return p.getId();
    }

    private PadInfoDTO padDto(String code, String layerCode) {
        PadInfoDTO dto = new PadInfoDTO();
        dto.setPadCode(code);
        dto.setMoldType("模具-" + code);
        dto.setShelfLayerCode(layerCode);
        return dto;
    }

    private BindLayerDTO bindDto(Long padId, String layerCode) {
        BindLayerDTO dto = new BindLayerDTO();
        dto.setPadId(padId);
        dto.setLayerCode(layerCode);
        return dto;
    }

    // ---------------- 绑定 / 换绑 ----------------

    /** 绑定：目标层位占用达到配额时拒绝，层位数据不变 */
    @Test
    void bind_toFullLayer_rejected() {
        layer("A-01-01", 1);
        layer("A-01-02", 5);
        padOnShelf("P-001", "A-01-01");
        Long freePad = padOnShelf("P-002", "A-01-02");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> padInfoService.bindLayer(bindDto(freePad, "A-01-01")));
        assertTrue(ex.getMessage().contains("已满"));

        // 数据未变：P-002 仍在原层位，A-01-01 占用仍为 1
        assertEquals("A-01-02", padInfoMapper.selectById(freePad).getShelfLayerCode());
        assertEquals(1, shelfLayerService.getByCode("A-01-01").getPadCount());
    }

    /** 换绑：目标层位已满时拒绝；换到有余量层位成功且占用数同步刷新 */
    @Test
    void rebind_respectsCapacityAndRefreshesCounts() {
        layer("A-01-01", 1);
        layer("A-01-02", 2);
        Long moving = padOnShelf("P-001", "A-01-01");
        padOnShelf("P-002", "A-01-02");

        // A-01-02 再放 1 块即满：先补一块占满
        padOnShelf("P-003", "A-01-02");
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> padInfoService.bindLayer(bindDto(moving, "A-01-02")));
        assertTrue(ex.getMessage().contains("已满"));

        // 换绑到新建的空层位成功，双方占用数刷新
        layer("B-01-01", 3);
        padInfoService.bindLayer(bindDto(moving, "B-01-01"));
        assertEquals(0, shelfLayerService.getByCode("A-01-01").getPadCount());
        assertEquals(1, shelfLayerService.getByCode("B-01-01").getPadCount());
        assertEquals("B-01-01", padInfoMapper.selectById(moving).getShelfLayerCode());
    }

    // ---------------- 新建档案带层位 ----------------

    /** 新建档案带层位：目标层位已满时拒绝建档 */
    @Test
    void create_withFullLayer_rejected() {
        layer("A-01-01", 1);
        padOnShelf("P-001", "A-01-01");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> padInfoService.create(padDto("P-002", "A-01-01")));
        assertTrue(ex.getMessage().contains("已满"));
        assertEquals(0, padInfoMapper.selectCount(
                new LambdaQueryWrapper<PadInfo>().eq(PadInfo::getPadCode, "P-002")));
    }

    /** 新建档案带层位成功：占用数与 BIND 调整记录同步落库 */
    @Test
    void create_withLayer_writesBindRecordAndCount() {
        layer("A-01-01", 2);
        PadInfo created = padInfoService.create(padDto("P-001", "A-01-01"));
        assertNotNull(created.getId());

        ShelfLayer layer = shelfLayerService.getByCode("A-01-01");
        assertEquals(1, layer.getPadCount());
        assertEquals(2, layer.getCapacity());

        List<LayerAdjustRecord> records = recordMapper.selectList(null);
        assertEquals(1, records.size());
        assertEquals("BIND", records.get(0).getAdjustType());
        assertEquals("A-01-01", records.get(0).getNewLayerCode());
    }

    // ---------------- 批量导入 ----------------

    /** 批量导入：同一层位逐行占用，达到配额后后续行失败，已导入行不受影响 */
    @Test
    void importRow_stopsAtCapacity() {
        layer("A-01-01", 2);

        PadInfo p1 = new PadInfo();
        p1.setPadCode("IMP-001");
        p1.setShelfLayerCode("A-01-01");
        padInfoService.importRow(p1);

        PadInfo p2 = new PadInfo();
        p2.setPadCode("IMP-002");
        p2.setShelfLayerCode("A-01-01");
        padInfoService.importRow(p2);

        // 第三行超出配额：单行失败，前两行保留
        PadInfo p3 = new PadInfo();
        p3.setPadCode("IMP-003");
        p3.setShelfLayerCode("A-01-01");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> padInfoService.importRow(p3));
        assertTrue(ex.getMessage().contains("已满"));

        assertEquals(2, shelfLayerService.getByCode("A-01-01").getPadCount());
        assertEquals(2, padInfoMapper.selectCount(null));
        // 两行各写一条 BIND 调整记录，失败行无记录
        assertEquals(2, recordMapper.selectCount(null));
    }

    // ---------------- 归还上架 ----------------

    /** 已满层位（含配额为 0 的空层）不能作为归还目标 */
    @Test
    void return_toFullLayer_rejected() {
        layer("A-01-01", 1);
        layer("A-01-02", 0);
        layer("B-01-01", 5);
        Long borrowedPad = padOnShelf("P-001", "B-01-01");
        padOnShelf("P-002", "A-01-01");

        PadCheckoutDTO checkout = new PadCheckoutDTO();
        checkout.setPadId(borrowedPad);
        checkout.setBorrower("张三");
        checkout.setProductionLine("冲压一线");
        PadBorrowRecord record = padBorrowService.checkout(checkout);

        // 目标层位已满（1/1）：拒绝（该层同时被占用，占用校验同样拦截）
        PadReturnDTO toFull = new PadReturnDTO();
        toFull.setId(record.getId());
        toFull.setReturnLayerCode("A-01-01");
        assertThrows(RuntimeException.class, () -> padBorrowService.doReturn(toFull));

        // 配额为 0 的空层位：视为已满，拒绝归还
        PadReturnDTO toZero = new PadReturnDTO();
        toZero.setId(record.getId());
        toZero.setReturnLayerCode("A-01-02");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> padBorrowService.doReturn(toZero));
        assertTrue(ex.getMessage().contains("已满"));

        // 记录仍未归还，垫板仍离架
        assertEquals("BORROWED", padBorrowRecordMapper.selectById(record.getId()).getStatus());
        assertNull(padInfoMapper.selectById(borrowedPad).getShelfLayerCode());
    }

    /** 归还到有余量的空闲层位成功：占用数、档案绑定与 RETURN 记录一致 */
    @Test
    void return_toLayerWithQuota_succeeds() {
        layer("A-01-01", 1);
        layer("B-01-01", 5);
        Long borrowedPad = padOnShelf("P-001", "B-01-01");

        PadCheckoutDTO checkout = new PadCheckoutDTO();
        checkout.setPadId(borrowedPad);
        checkout.setBorrower("李四");
        checkout.setProductionLine("冲压二线");
        PadBorrowRecord record = padBorrowService.checkout(checkout);
        assertEquals(0, shelfLayerService.getByCode("B-01-01").getPadCount());

        PadReturnDTO ret = new PadReturnDTO();
        ret.setId(record.getId());
        ret.setReturnLayerCode("A-01-01");
        padBorrowService.doReturn(ret);

        // 归还后：垫板重新上架，层位占用与调整记录一致
        assertEquals("A-01-01", padInfoMapper.selectById(borrowedPad).getShelfLayerCode());
        assertEquals(1, shelfLayerService.getByCode("A-01-01").getPadCount());
        List<LayerAdjustRecord> records = recordMapper.selectList(null);
        assertEquals(2, records.size());
        assertTrue(records.stream().anyMatch(r -> "CHECKOUT".equals(r.getAdjustType())));
        assertTrue(records.stream().anyMatch(r -> "RETURN".equals(r.getAdjustType())
                && "A-01-01".equals(r.getNewLayerCode())));
    }

    // ---------------- 配额调整 ----------------

    /** 下调配额不得低于当前在架数；等于在架数允许，但此后该层不可再上架 */
    @Test
    void decreaseQuota_belowOccupancy_rejected() {
        ShelfLayer layer = layer("A-01-01", 5);
        padOnShelf("P-001", "A-01-01");
        padOnShelf("P-002", "A-01-01");

        ShelfLayer update = new ShelfLayer();
        update.setId(layer.getId());
        update.setCapacity(1);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> shelfLayerService.save(update));
        assertTrue(ex.getMessage().contains("在架数"));
        assertEquals(5, shelfLayerMapper.selectById(layer.getId()).getCapacity());

        // 下调到等于在架数：允许；此后该层已满，不可再绑定
        update.setCapacity(2);
        shelfLayerService.save(update);
        assertEquals(2, shelfLayerMapper.selectById(layer.getId()).getCapacity());

        Long freePad = padOnShelf("P-003", null);
        RuntimeException bindEx = assertThrows(RuntimeException.class,
                () -> padInfoService.bindLayer(bindDto(freePad, "A-01-01")));
        assertTrue(bindEx.getMessage().contains("已满"));
    }

    /** 上调配额后恢复可上架；新建分层未指定配额时使用默认值 */
    @Test
    void increaseQuota_allowsBindingAgain_andDefaultCapacity() {
        ShelfLayer layer = layer("A-01-01", 1);
        padOnShelf("P-001", "A-01-01");
        Long freePad = padOnShelf("P-002", null);
        assertThrows(RuntimeException.class,
                () -> padInfoService.bindLayer(bindDto(freePad, "A-01-01")));

        ShelfLayer update = new ShelfLayer();
        update.setId(layer.getId());
        update.setCapacity(3);
        shelfLayerService.save(update);
        padInfoService.bindLayer(bindDto(freePad, "A-01-01"));
        assertEquals(2, shelfLayerService.getByCode("A-01-01").getPadCount());

        // 新建分层不传配额：默认 10
        ShelfLayer created = new ShelfLayer();
        created.setLayerCode("C-01-01");
        created.setShelfCode("C-01");
        created.setLayerName("C区01货架第1层");
        shelfLayerService.save(created);
        assertEquals(ShelfLayerService.DEFAULT_CAPACITY,
                shelfLayerMapper.selectById(created.getId()).getCapacity());
    }

    /** 非法配额值（负数）拒绝保存 */
    @Test
    void save_negativeCapacity_rejected() {
        ShelfLayer layer = new ShelfLayer();
        layer.setLayerCode("A-01-01");
        layer.setShelfCode("A-01");
        layer.setCapacity(-1);
        assertThrows(RuntimeException.class, () -> shelfLayerService.save(layer));
        assertEquals(0, shelfLayerMapper.selectCount(null));
    }

    /** 档案编辑换层：目标层位已满时拒绝，原绑定保持不变 */
    @Test
    void update_toFullLayer_rejected() {
        layer("A-01-01", 1);
        layer("A-01-02", 5);
        padOnShelf("P-001", "A-01-01");
        PadInfo pad = padInfoService.create(padDto("P-002", "A-01-02"));

        PadInfoDTO dto = padDto("P-002", "A-01-01");
        dto.setId(pad.getId());
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> padInfoService.update(dto));
        assertTrue(ex.getMessage().contains("已满"));
        assertEquals("A-01-02", padInfoMapper.selectById(pad.getId()).getShelfLayerCode());
    }
}
