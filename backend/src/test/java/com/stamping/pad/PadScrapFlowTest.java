package com.stamping.pad;

import com.stamping.pad.dto.BindLayerDTO;
import com.stamping.pad.dto.PadCheckoutDTO;
import com.stamping.pad.dto.PadMaintenanceDTO;
import com.stamping.pad.dto.PadScrapDTO;
import com.stamping.pad.dto.ScrapRecordQueryDTO;
import com.stamping.pad.entity.LayerAdjustRecord;
import com.stamping.pad.entity.PadInfo;
import com.stamping.pad.entity.PadScrapRecord;
import com.stamping.pad.entity.ShelfLayer;
import com.stamping.pad.mapper.LayerAdjustRecordMapper;
import com.stamping.pad.mapper.LayerBlockRecordMapper;
import com.stamping.pad.mapper.PadBorrowRecordMapper;
import com.stamping.pad.mapper.PadInfoMapper;
import com.stamping.pad.mapper.PadMaintenanceRecordMapper;
import com.stamping.pad.mapper.PadScrapRecordMapper;
import com.stamping.pad.mapper.ShelfLayerMapper;
import com.stamping.pad.service.PadBorrowService;
import com.stamping.pad.service.PadInfoService;
import com.stamping.pad.service.PadMaintenanceService;
import com.stamping.pad.service.PadScrapService;
import com.stamping.pad.service.ShelfLayerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 垫板报废出库链路：报废建议 -> 库房报废出库登记 -> 离架释放占用 -> 终态冻结（不可领用/归还/上架/编辑/再保养/重复报废）。
 */
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PadScrapFlowTest {

    @Autowired
    private PadScrapService scrapService;
    @Autowired
    private PadMaintenanceService maintenanceService;
    @Autowired
    private PadBorrowService padBorrowService;
    @Autowired
    private PadInfoService padInfoService;
    @Autowired
    private ShelfLayerService shelfLayerService;
    @Autowired
    private ShelfLayerMapper shelfLayerMapper;
    @Autowired
    private PadInfoMapper padInfoMapper;
    @Autowired
    private PadBorrowRecordMapper padBorrowRecordMapper;
    @Autowired
    private PadMaintenanceRecordMapper maintenanceRecordMapper;
    @Autowired
    private PadScrapRecordMapper scrapRecordMapper;
    @Autowired
    private LayerAdjustRecordMapper adjustRecordMapper;
    @Autowired
    private LayerBlockRecordMapper blockRecordMapper;

    private Long padId;

    @BeforeEach
    void setUp() {
        adjustRecordMapper.delete(null);
        scrapRecordMapper.delete(null);
        maintenanceRecordMapper.delete(null);
        padBorrowRecordMapper.delete(null);
        blockRecordMapper.delete(null);
        padInfoMapper.delete(null);
        shelfLayerMapper.delete(null);

        ShelfLayer layer = new ShelfLayer();
        layer.setLayerCode("A-01-01");
        layer.setShelfCode("A-01");
        layer.setLayerName("A区01货架第1层");
        layer.setLayerOrder(1);
        shelfLayerMapper.insert(layer);

        PadInfo pad = new PadInfo();
        pad.setPadCode("S-001");
        pad.setMoldType("模具-S");
        pad.setShelfLayerCode("A-01-01");
        pad.setBindTime(LocalDateTime.now());
        pad.setMaintenanceStatus("AVAILABLE");
        pad.setCreateTime(LocalDateTime.now());
        pad.setUpdateTime(LocalDateTime.now());
        padInfoMapper.insert(pad);
        padId = pad.getId();
    }

    private PadMaintenanceDTO suggestion() {
        PadMaintenanceDTO dto = new PadMaintenanceDTO();
        dto.setPadId(padId);
        dto.setMaintenanceType("故障维修");
        dto.setHandler("维修工甲");
        dto.setMaintenanceResult("SCRAPPED");
        dto.setStatusAfter("DISABLED");
        dto.setRemark("磨损超限，建议报废");
        return dto;
    }

    private PadScrapDTO scrapDto() {
        PadScrapDTO dto = new PadScrapDTO();
        dto.setPadId(padId);
        dto.setApprover("车间主任");
        dto.setDestination("废品仓暂存");
        dto.setPhotoPaths(List.of("/files/scrap1.jpg", "/files/scrap2.jpg"));
        dto.setRemark("双人确认后出库");
        return dto;
    }

    /** 无报废建议时禁止出库 */
    @Test
    void outbound_withoutSuggestionRejected() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> scrapService.outbound(scrapDto()));
        assertTrue(ex.getMessage().contains("报废建议"));
        assertEquals(0, scrapRecordMapper.selectCount(null));
        // 垫板仍在架，状态不变
        assertEquals("AVAILABLE", padInfoMapper.selectById(padId).getMaintenanceStatus());
        assertEquals("A-01-01", padInfoMapper.selectById(padId).getShelfLayerCode());
    }

    /** 缺照片/批准人/去向被拒绝 */
    @Test
    void outbound_missingRequiredFieldsRejected() {
        maintenanceService.register(suggestion());

        PadScrapDTO noPhoto = scrapDto();
        noPhoto.setPhotoPaths(List.of());
        RuntimeException photoEx = assertThrows(RuntimeException.class, () -> scrapService.outbound(noPhoto));
        assertTrue(photoEx.getMessage().contains("照片"));

        PadScrapDTO noApprover = scrapDto();
        noApprover.setApprover(" ");
        assertTrue(assertThrows(RuntimeException.class, () -> scrapService.outbound(noApprover))
                .getMessage().contains("批准人"));

        PadScrapDTO noDestination = scrapDto();
        noDestination.setDestination("");
        assertTrue(assertThrows(RuntimeException.class, () -> scrapService.outbound(noDestination))
                .getMessage().contains("去向"));
        assertEquals(0, scrapRecordMapper.selectCount(null));
    }

    /** 正常出库：状态置 SCRAPPED、离架释放占用、写 SCRAP 调整记录、照片落库 */
    @Test
    void outbound_success_marksScrappedAndFreesLayer() {
        maintenanceService.register(suggestion()); // DISABLED 自动离架并写 UNBIND
        assertEquals(0, shelfLayerService.countOnShelf("A-01-01"));

        PadScrapRecord record = scrapService.outbound(scrapDto());

        PadInfo pad = padInfoMapper.selectById(padId);
        assertEquals("SCRAPPED", pad.getMaintenanceStatus());
        assertNull(pad.getShelfLayerCode());
        assertNull(pad.getBindTime());
        assertEquals(0, shelfLayerService.countOnShelf("A-01-01"));
        // 保养 DISABLED 时已离架，报废不再重复写层位调整记录（仅 UNBIND 一条）
        List<LayerAdjustRecord> adjusts = adjustRecordMapper.selectList(null);
        assertEquals(1, adjusts.size());
        assertEquals("UNBIND", adjusts.get(0).getAdjustType());

        assertNotNull(record.getId());
        assertEquals("车间主任", record.getApprover());
        assertEquals("废品仓暂存", record.getDestination());
        assertEquals(2, record.getPhotos().size());
    }

    /** 在架垫板（建议时仍为可用）直接报废：写 SCRAP 调整记录并立即释放层位 */
    @Test
    void outbound_fromOnShelf_writesScrapAdjustAndFreesOccupancy() {
        // 报废建议但状态保持可用（在架）
        PadMaintenanceDTO availableSuggestion = suggestion();
        availableSuggestion.setStatusAfter("AVAILABLE");
        maintenanceService.register(availableSuggestion);
        assertEquals(1, shelfLayerService.countOnShelf("A-01-01"));

        scrapService.outbound(scrapDto());

        PadInfo pad = padInfoMapper.selectById(padId);
        assertEquals("SCRAPPED", pad.getMaintenanceStatus());
        assertNull(pad.getShelfLayerCode());
        assertEquals(0, shelfLayerService.countOnShelf("A-01-01"));

        List<LayerAdjustRecord> adjusts = adjustRecordMapper.selectList(null);
        assertEquals(1, adjusts.size());
        LayerAdjustRecord scrapAdjust = adjusts.get(0);
        assertEquals("SCRAP", scrapAdjust.getAdjustType());
        assertEquals("A-01-01", scrapAdjust.getOldLayerCode());
        assertNull(scrapAdjust.getNewLayerCode());
        assertEquals("车间主任", scrapAdjust.getOperator());
        assertTrue(scrapAdjust.getAdjustReason().contains("废品仓暂存"));
    }

    /** 重复报废被拒绝（唯一记录 + 状态双重防护） */
    @Test
    void outbound_duplicateRejected() {
        maintenanceService.register(suggestion());
        scrapService.outbound(scrapDto());
        assertThrows(RuntimeException.class, () -> scrapService.outbound(scrapDto()));
        assertEquals(1, scrapRecordMapper.selectCount(null));
    }

    /** 领用中的垫板须先归还，不能直接报废 */
    @Test
    void outbound_borrowedPadRejected() {
        // 先给出建议（DISABLED 会导致无法领用，因此用可用态建议）
        PadMaintenanceDTO availableSuggestion = suggestion();
        availableSuggestion.setStatusAfter("AVAILABLE");
        maintenanceService.register(availableSuggestion);

        PadCheckoutDTO checkout = new PadCheckoutDTO();
        checkout.setPadId(padId);
        checkout.setBorrower("张三");
        checkout.setProductionLine("冲压一线");
        padBorrowService.checkout(checkout);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> scrapService.outbound(scrapDto()));
        assertTrue(ex.getMessage().contains("先归还"));
        assertEquals(0, scrapRecordMapper.selectCount(null));
    }

    /** 报废后终态冻结：领用、归还、绑定、保养、编辑、删除全部被拒绝 */
    @Test
    void scrappedPad_isFrozenAcrossAllFlows() {
        maintenanceService.register(suggestion());
        scrapService.outbound(scrapDto());

        // 领用
        PadCheckoutDTO checkout = new PadCheckoutDTO();
        checkout.setPadId(padId);
        checkout.setBorrower("张三");
        checkout.setProductionLine("冲压一线");
        assertTrue(assertThrows(RuntimeException.class, () -> padBorrowService.checkout(checkout))
                .getMessage().contains("报废"));

        // 绑定 / 解绑
        BindLayerDTO bind = new BindLayerDTO();
        bind.setPadId(padId);
        bind.setLayerCode("A-01-01");
        assertTrue(assertThrows(RuntimeException.class, () -> padInfoService.bindLayer(bind))
                .getMessage().contains("报废"));

        // 再登记保养
        assertTrue(assertThrows(RuntimeException.class,
                () -> maintenanceService.register(suggestion())).getMessage().contains("报废"));

        // 编辑档案
        com.stamping.pad.dto.PadInfoDTO edit = new com.stamping.pad.dto.PadInfoDTO();
        edit.setId(padId);
        edit.setPadCode("S-001");
        edit.setRemark("试图编辑");
        assertTrue(assertThrows(RuntimeException.class, () -> padInfoService.update(edit))
                .getMessage().contains("冻结"));

        // 删除
        assertTrue(assertThrows(RuntimeException.class, () -> padInfoService.delete(padId))
                .getMessage().contains("报废"));
    }

    /** 报废后归还历史未结记录被拒绝（领用中->报废的非法路径后端兜底） */
    @Test
    void scrappedPad_cannotBeReturned() {
        // 直接构造“已报废但仍有领用中记录”的异常存量场景，验证 doReturn 的状态兜底
        PadMaintenanceDTO availableSuggestion = suggestion();
        availableSuggestion.setStatusAfter("AVAILABLE");
        maintenanceService.register(availableSuggestion);
        PadCheckoutDTO checkout = new PadCheckoutDTO();
        checkout.setPadId(padId);
        checkout.setBorrower("张三");
        checkout.setProductionLine("冲压一线");
        var borrowed = padBorrowService.checkout(checkout);

        // 手工置为报废态（绕过出库前置校验，模拟异常数据）
        PadInfo patch = padInfoMapper.selectById(padId);
        patch.setMaintenanceStatus("SCRAPPED");
        padInfoMapper.updateById(patch);

        com.stamping.pad.dto.PadReturnDTO ret = new com.stamping.pad.dto.PadReturnDTO();
        ret.setId(borrowed.getId());
        ret.setReturnLayerCode("A-01-01");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> padBorrowService.doReturn(ret));
        assertTrue(ex.getMessage().contains("报废"));
    }

    /** 列表按去向、批准人、垫板编号、日期区间筛选 */
    @Test
    void page_filtersByDestinationApproverCodeAndDate() {
        maintenanceService.register(suggestion());
        scrapService.outbound(scrapDto());

        ScrapRecordQueryDTO byDestination = new ScrapRecordQueryDTO();
        byDestination.setDestination("废品仓");
        assertEquals(1, scrapService.pageList(byDestination).getRecords().size());

        ScrapRecordQueryDTO byApprover = new ScrapRecordQueryDTO();
        byApprover.setApprover("主任");
        assertEquals(1, scrapService.pageList(byApprover).getRecords().size());

        ScrapRecordQueryDTO byCode = new ScrapRecordQueryDTO();
        byCode.setPadCode("S-");
        assertEquals(1, scrapService.pageList(byCode).getRecords().size());

        ScrapRecordQueryDTO future = new ScrapRecordQueryDTO();
        future.setStartTime(LocalDateTime.now().plusDays(1));
        assertEquals(0, scrapService.pageList(future).getRecords().size());

        ScrapRecordQueryDTO past = new ScrapRecordQueryDTO();
        past.setEndTime(LocalDateTime.now().plusHours(1));
        assertEquals(1, scrapService.pageList(past).getRecords().size());

        ScrapRecordQueryDTO miss = new ScrapRecordQueryDTO();
        miss.setDestination("回收商");
        assertEquals(0, scrapService.pageList(miss).getRecords().size());
    }

    /** 统计：报废档案数与报废记录数一致 */
    @Test
    void statistics_countsScrappedPadsAndRecords() {
        maintenanceService.register(suggestion());
        scrapService.outbound(scrapDto());

        assertEquals(1L, scrapService.statistics().get("scrappedCount"));
        assertEquals(1L, scrapService.statistics().get("scrapRecordCount"));
        assertEquals(1L, maintenanceService.statistics().get("scrappedCount"));
    }
}
