package com.stamping.pad;

import com.stamping.pad.dto.PadCheckoutDTO;
import com.stamping.pad.dto.PadMaintenanceDTO;
import com.stamping.pad.dto.PadReturnDTO;
import com.stamping.pad.entity.PadBorrowRecord;
import com.stamping.pad.entity.PadInfo;
import com.stamping.pad.entity.PadMaintenanceRecord;
import com.stamping.pad.entity.ShelfLayer;
import com.stamping.pad.mapper.PadBorrowRecordMapper;
import com.stamping.pad.mapper.PadInfoMapper;
import com.stamping.pad.mapper.PadMaintenanceRecordMapper;
import com.stamping.pad.mapper.ShelfLayerMapper;
import com.stamping.pad.service.PadBorrowService;
import com.stamping.pad.service.PadMaintenanceService;
import com.stamping.pad.vo.PadMaintenanceDetailVO;
import com.stamping.pad.dto.MaintenanceRecordQueryDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 垫板保养台账链路：登记保养更新状态、待检/停用不可领用与归还、筛选与详情时间线。
 */
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PadMaintenanceFlowTest {

    @Autowired
    private PadMaintenanceService maintenanceService;
    @Autowired
    private PadBorrowService padBorrowService;
    @Autowired
    private ShelfLayerMapper shelfLayerMapper;
    @Autowired
    private PadInfoMapper padInfoMapper;
    @Autowired
    private PadBorrowRecordMapper padBorrowRecordMapper;
    @Autowired
    private PadMaintenanceRecordMapper maintenanceRecordMapper;

    private Long padId;

    @BeforeEach
    void setUp() {
        maintenanceRecordMapper.delete(null);
        padBorrowRecordMapper.delete(null);
        padInfoMapper.delete(null);
        shelfLayerMapper.delete(null);

        ShelfLayer layer = new ShelfLayer();
        layer.setLayerCode("A-01-01");
        layer.setShelfCode("A-01");
        layer.setLayerName("A区01货架第1层");
        layer.setLayerOrder(1);
        shelfLayerMapper.insert(layer);

        PadInfo pad = new PadInfo();
        pad.setPadCode("M-001");
        pad.setMoldType("模具-M");
        pad.setLength(new BigDecimal("1000.00"));
        pad.setWidth(new BigDecimal("800.00"));
        pad.setThickness(new BigDecimal("20.00"));
        pad.setShelfLayerCode("A-01-01");
        pad.setBindTime(LocalDateTime.now());
        pad.setMaintenanceStatus("AVAILABLE");
        pad.setCreateTime(LocalDateTime.now());
        pad.setUpdateTime(LocalDateTime.now());
        padInfoMapper.insert(pad);
        padId = pad.getId();
    }

    private PadMaintenanceDTO maintenanceDto(String type, String result, String statusAfter) {
        PadMaintenanceDTO dto = new PadMaintenanceDTO();
        dto.setPadId(padId);
        dto.setMaintenanceType(type);
        dto.setHandler("维修工甲");
        dto.setMaintenanceResult(result);
        dto.setStatusAfter(statusAfter);
        dto.setRemark("保养备注");
        return dto;
    }

    /** 登记保养后垫板状态同步更新，保养记录保留前后状态 */
    @Test
    void register_updatesPadStatusAndKeepsHistory() {
        maintenanceService.register(maintenanceDto("定期保养", "NORMAL", "PENDING"));
        assertEquals("PENDING", padInfoMapper.selectById(padId).getMaintenanceStatus());

        maintenanceService.register(maintenanceDto("送检", "REPAIRED", "AVAILABLE"));
        PadInfo pad = padInfoMapper.selectById(padId);
        assertEquals("AVAILABLE", pad.getMaintenanceStatus());

        List<PadMaintenanceRecord> records = maintenanceService.listByPadId(padId);
        assertEquals(2, records.size());
        // 列表按保养时间倒序：最新一条为恢复可用，前状态为待检
        assertEquals("PENDING", records.get(0).getStatusBefore());
        assertEquals("AVAILABLE", records.get(0).getStatusAfter());
        assertEquals("AVAILABLE", records.get(1).getStatusBefore());
        assertEquals("PENDING", records.get(1).getStatusAfter());
    }

    /** 待检/停用垫板禁止领用；恢复可用后可领用 */
    @Test
    void pendingOrDisabledPad_cannotCheckout() {
        maintenanceService.register(maintenanceDto("故障维修", "ABNORMAL", "PENDING"));

        PadCheckoutDTO checkout = new PadCheckoutDTO();
        checkout.setPadId(padId);
        checkout.setBorrower("张三");
        checkout.setProductionLine("冲压一线");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> padBorrowService.checkout(checkout));
        assertTrue(ex.getMessage().contains("待检"));

        maintenanceService.register(maintenanceDto("故障维修", "REPAIRED", "AVAILABLE"));
        PadBorrowRecord record = padBorrowService.checkout(checkout);
        assertEquals("BORROWED", record.getStatus());
    }

    /** 停用垫板同样禁止领用 */
    @Test
    void disabledPad_cannotCheckout() {
        maintenanceService.register(maintenanceDto("故障维修", "SCRAPPED", "DISABLED"));

        PadCheckoutDTO checkout = new PadCheckoutDTO();
        checkout.setPadId(padId);
        checkout.setBorrower("李四");
        checkout.setProductionLine("冲压二线");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> padBorrowService.checkout(checkout));
        assertTrue(ex.getMessage().contains("停用"));
    }

    /** 领用中被标记为停用/待检的垫板，归还时被拒绝；恢复可用后可归还上架 */
    @Test
    void nonAvailablePad_cannotBeReturnedUntilRecovered() {
        // 先正常领用离架
        PadCheckoutDTO checkout = new PadCheckoutDTO();
        checkout.setPadId(padId);
        checkout.setBorrower("王五");
        checkout.setProductionLine("冲压三线");
        PadBorrowRecord borrowed = padBorrowService.checkout(checkout);
        assertNull(padInfoMapper.selectById(padId).getShelfLayerCode());

        // 领用期间保养标记为停用
        maintenanceService.register(maintenanceDto("故障维修", "ABNORMAL", "DISABLED"));

        // 腾出空闲层位：另建一块垫板占用 A-01-02 不现实，直接归还原层位当前为空
        // （M-001 已离架，A-01-01 空闲），但因停用应先被状态校验拒绝
        PadReturnDTO ret = new PadReturnDTO();
        ret.setId(borrowed.getId());
        ret.setReturnLayerCode("A-01-01");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> padBorrowService.doReturn(ret));
        assertTrue(ex.getMessage().contains("停用"));
        // 记录仍是领用中，垫板仍离架
        assertEquals("BORROWED", padBorrowRecordMapper.selectById(borrowed.getId()).getStatus());

        maintenanceService.register(maintenanceDto("复检", "REPAIRED", "AVAILABLE"));
        PadBorrowRecord returned = padBorrowService.doReturn(ret);
        assertEquals("RETURNED", returned.getStatus());
        assertEquals("A-01-01", padInfoMapper.selectById(padId).getShelfLayerCode());
    }

    /** 非法状态/结果被拒绝 */
    @Test
    void register_illegalStatusRejected() {
        PadMaintenanceDTO dto = maintenanceDto("日常保养", "NORMAL", "UNKNOWN");
        assertThrows(RuntimeException.class, () -> maintenanceService.register(dto));
        assertEquals("AVAILABLE", padInfoMapper.selectById(padId).getMaintenanceStatus());
    }

    /** 按状态（垫板当前状态）、编号、保养时间区间筛选 */
    @Test
    void query_filtersByStatusPadCodeAndDate() {
        maintenanceService.register(maintenanceDto("日常保养", "NORMAL", "AVAILABLE"));
        maintenanceService.register(maintenanceDto("送检", "ABNORMAL", "PENDING"));

        MaintenanceRecordQueryDTO pending = new MaintenanceRecordQueryDTO();
        pending.setStatus("PENDING");
        // 两条记录同属一块垫板、当前状态为待检，因此按当前状态筛选均命中
        assertEquals(2, maintenanceService.pageList(pending).getRecords().size());

        MaintenanceRecordQueryDTO byCode = new MaintenanceRecordQueryDTO();
        byCode.setPadCode("M-");
        assertEquals(2, maintenanceService.pageList(byCode).getRecords().size());

        MaintenanceRecordQueryDTO future = new MaintenanceRecordQueryDTO();
        future.setStartTime(LocalDateTime.now().plusDays(1));
        assertEquals(0, maintenanceService.pageList(future).getRecords().size());

        MaintenanceRecordQueryDTO past = new MaintenanceRecordQueryDTO();
        past.setEndTime(LocalDateTime.now().plusHours(1));
        assertEquals(2, maintenanceService.pageList(past).getRecords().size());
    }

    /** 详情包含最近一次保养与状态变更时间线（首次保养 AVAILABLE→PENDING 算变更） */
    @Test
    void detail_showsLatestAndStatusChanges() {
        // AVAILABLE -> PENDING（变更）
        maintenanceService.register(maintenanceDto("送检", "ABNORMAL", "PENDING"));
        // PENDING -> PENDING（保养但状态不变，不进状态变更时间线）
        maintenanceService.register(maintenanceDto("清洁润滑", "NORMAL", "PENDING"));
        // PENDING -> AVAILABLE（变更）
        maintenanceService.register(maintenanceDto("复检", "REPAIRED", "AVAILABLE"));

        PadMaintenanceDetailVO detail = maintenanceService.detail(padId);
        assertNotNull(detail.getPad());
        assertEquals("AVAILABLE", detail.getPad().getMaintenanceStatus());
        assertNotNull(detail.getLatestRecord());
        assertEquals("复检", detail.getLatestRecord().getMaintenanceType());
        assertEquals(3, detail.getMaintenanceRecords().size());
        assertEquals(2, detail.getStatusChangeRecords().size());
    }
}
