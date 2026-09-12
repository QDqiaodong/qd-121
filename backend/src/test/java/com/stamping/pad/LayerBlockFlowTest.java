package com.stamping.pad;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stamping.pad.dto.BindLayerDTO;
import com.stamping.pad.dto.LayerBlockDTO;
import com.stamping.pad.dto.LayerBlockRecordQueryDTO;
import com.stamping.pad.dto.LayerBlockReleaseDTO;
import com.stamping.pad.dto.PadCheckoutDTO;
import com.stamping.pad.dto.PadInfoDTO;
import com.stamping.pad.dto.PadReturnDTO;
import com.stamping.pad.entity.LayerBlockRecord;
import com.stamping.pad.entity.PadBorrowRecord;
import com.stamping.pad.entity.PadInfo;
import com.stamping.pad.entity.ShelfLayer;
import com.stamping.pad.mapper.LayerAdjustRecordMapper;
import com.stamping.pad.mapper.LayerBlockRecordMapper;
import com.stamping.pad.mapper.LayerCapacityExpandRecordMapper;
import com.stamping.pad.mapper.PadBorrowRecordMapper;
import com.stamping.pad.mapper.PadInfoMapper;
import com.stamping.pad.mapper.ShelfLayerMapper;
import com.stamping.pad.service.LayerBlockService;
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
 * 层位封锁链路：破损/待清扫/检修登记封锁（原因、开始时间、经办人）后，
 * 该层禁止绑定/换绑/建档上架/归还上架/导入占位；解除封锁必须填写结论；
 * 解除后层位恢复可上架，台账可按状态与日期回看。
 */
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LayerBlockFlowTest {

    @Autowired
    private LayerBlockService layerBlockService;
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

    private LayerBlockDTO blockDto(String layerCode) {
        LayerBlockDTO dto = new LayerBlockDTO();
        dto.setLayerCode(layerCode);
        dto.setBlockType("DAMAGE");
        dto.setBlockReason("层板变形破损，需更换");
        dto.setOperator("王库管");
        return dto;
    }

    private LayerBlockReleaseDTO releaseDto(Long id, String conclusion) {
        LayerBlockReleaseDTO dto = new LayerBlockReleaseDTO();
        dto.setId(id);
        dto.setReleaseConclusion(conclusion);
        return dto;
    }

    // ---------------- 登记封锁 ----------------

    /** 登记封锁：写入原因、开始时间与经办人，层位列表同步带出“封锁中”状态 */
    @Test
    void block_writesRecordAndLayerShowsBlocked() {
        layer("A-01-01", 5);
        LocalDateTime start = LocalDateTime.now().minusHours(1);

        LayerBlockDTO dto = blockDto("A-01-01");
        dto.setStartTime(start);
        dto.setBlockType("MAINTENANCE");
        LayerBlockRecord record = layerBlockService.block(dto);

        assertNotNull(record.getId());
        assertEquals("BLOCKED", record.getStatus());
        assertEquals("MAINTENANCE", record.getBlockType());
        assertEquals("层板变形破损，需更换", record.getBlockReason());
        assertEquals("王库管", record.getOperator());
        assertEquals(start, record.getStartTime());

        // 层位列表/单查均带出封锁信息，前端据此限制可选范围
        ShelfLayer layer = shelfLayerService.getByCode("A-01-01");
        assertNotNull(layer.getActiveBlock());
        assertEquals("层板变形破损，需更换", layer.getActiveBlock().getBlockReason());
        List<ShelfLayer> all = shelfLayerService.listAll();
        assertNotNull(all.get(0).getActiveBlock());
    }

    /** 必填校验：原因/经办人/类型缺失时拒绝登记 */
    @Test
    void block_missingRequiredFields_rejected() {
        layer("A-01-01", 5);

        LayerBlockDTO noReason = blockDto("A-01-01");
        noReason.setBlockReason(" ");
        assertThrows(RuntimeException.class, () -> layerBlockService.block(noReason));

        LayerBlockDTO noOperator = blockDto("A-01-01");
        noOperator.setOperator(null);
        assertThrows(RuntimeException.class, () -> layerBlockService.block(noOperator));

        LayerBlockDTO badType = blockDto("A-01-01");
        badType.setBlockType("UNKNOWN");
        assertThrows(RuntimeException.class, () -> layerBlockService.block(badType));

        LayerBlockDTO noLayer = blockDto("A-01-01");
        noLayer.setLayerCode("B-99-99");
        assertThrows(RuntimeException.class, () -> layerBlockService.block(noLayer));

        assertEquals(0, blockRecordMapper.selectCount(null));
    }

    /** 同一层位已封锁时禁止重复登记 */
    @Test
    void block_duplicateActiveBlock_rejected() {
        layer("A-01-01", 5);
        layerBlockService.block(blockDto("A-01-01"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> layerBlockService.block(blockDto("A-01-01")));
        assertTrue(ex.getMessage().contains("封锁中"));
        assertEquals(1, blockRecordMapper.selectCount(null));
    }

    // ---------------- 封锁期间禁止上架占位 ----------------

    /** 封锁层禁止绑定/换绑：目标层位校验在容量之前，直接拒绝 */
    @Test
    void blockedLayer_rejectsBindAndRebind() {
        layer("A-01-01", 5);
        layer("A-01-02", 5);
        Long freePad = padOnShelf("P-001", null);
        Long movingPad = padOnShelf("P-002", "A-01-02");
        layerBlockService.block(blockDto("A-01-01"));

        // 绑定到封锁层：拒绝
        BindLayerDTO bind = new BindLayerDTO();
        bind.setPadId(freePad);
        bind.setLayerCode("A-01-01");
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> padInfoService.bindLayer(bind));
        assertTrue(ex.getMessage().contains("封锁"));

        // 换绑到封锁层：拒绝
        BindLayerDTO rebind = new BindLayerDTO();
        rebind.setPadId(movingPad);
        rebind.setLayerCode("A-01-01");
        assertThrows(RuntimeException.class, () -> padInfoService.bindLayer(rebind));

        // 数据未变：两块垫板层位保持不变，封锁层占用仍为 0
        assertNull(padInfoMapper.selectById(freePad).getShelfLayerCode());
        assertEquals("A-01-02", padInfoMapper.selectById(movingPad).getShelfLayerCode());
        assertEquals(0, shelfLayerService.getByCode("A-01-01").getPadCount());
    }

    /** 封锁层禁止建档上架与档案编辑换层 */
    @Test
    void blockedLayer_rejectsCreateAndUpdate() {
        layer("A-01-01", 5);
        layer("A-01-02", 5);
        layerBlockService.block(blockDto("A-01-01"));

        PadInfoDTO create = new PadInfoDTO();
        create.setPadCode("P-001");
        create.setShelfLayerCode("A-01-01");
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> padInfoService.create(create));
        assertTrue(ex.getMessage().contains("封锁"));
        assertEquals(0, padInfoMapper.selectCount(
                new LambdaQueryWrapper<PadInfo>().eq(PadInfo::getPadCode, "P-001")));

        // 编辑换层到封锁层：拒绝，原绑定不变
        PadInfoDTO onFree = new PadInfoDTO();
        onFree.setPadCode("P-002");
        onFree.setShelfLayerCode("A-01-02");
        PadInfo pad = padInfoService.create(onFree);
        PadInfoDTO update = new PadInfoDTO();
        update.setId(pad.getId());
        update.setPadCode("P-002");
        update.setShelfLayerCode("A-01-01");
        assertThrows(RuntimeException.class, () -> padInfoService.update(update));
        assertEquals("A-01-02", padInfoMapper.selectById(pad.getId()).getShelfLayerCode());
    }

    /** 封锁层禁止导入占位 */
    @Test
    void blockedLayer_rejectsImportRow() {
        layer("A-01-01", 5);
        layerBlockService.block(blockDto("A-01-01"));

        PadInfo row = new PadInfo();
        row.setPadCode("IMP-001");
        row.setShelfLayerCode("A-01-01");
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> padInfoService.importRow(row));
        assertTrue(ex.getMessage().contains("封锁"));
        assertEquals(0, padInfoMapper.selectCount(null));
    }

    /** 封锁层禁止归还上架 */
    @Test
    void blockedLayer_rejectsReturn() {
        layer("A-01-01", 5);
        layer("B-01-01", 5);
        Long padId = padOnShelf("P-001", "B-01-01");

        PadCheckoutDTO checkout = new PadCheckoutDTO();
        checkout.setPadId(padId);
        checkout.setBorrower("张三");
        checkout.setProductionLine("冲压一线");
        PadBorrowRecord record = padBorrowService.checkout(checkout);

        // 领用后目标层位被封锁：归还到该层被拒
        layerBlockService.block(blockDto("A-01-01"));
        PadReturnDTO ret = new PadReturnDTO();
        ret.setId(record.getId());
        ret.setReturnLayerCode("A-01-01");
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> padBorrowService.doReturn(ret));
        assertTrue(ex.getMessage().contains("封锁"));

        // 记录仍未归还，垫板仍离架
        assertEquals("BORROWED", padBorrowRecordMapper.selectById(record.getId()).getStatus());
        assertNull(padInfoMapper.selectById(padId).getShelfLayerCode());
    }

    /** 封锁层上的在架垫板不被卡死：仍可解绑/领用离架，层位删除被拦截 */
    @Test
    void blockedLayer_unbindAndCheckoutStillAllowed_deleteRejected() {
        layer("A-01-01", 5);
        Long padId = padOnShelf("P-001", "A-01-01");
        layerBlockService.block(blockDto("A-01-01"));

        // 封锁中层位（无在架垫板时）禁止删除，避免封锁台账留下无主记录：先移出垫板再验证
        PadCheckoutDTO checkout = new PadCheckoutDTO();
        checkout.setPadId(padId);
        checkout.setBorrower("李四");
        checkout.setProductionLine("冲压二线");
        padBorrowService.checkout(checkout);
        assertNull(padInfoMapper.selectById(padId).getShelfLayerCode());

        ShelfLayer blocked = shelfLayerService.getByCode("A-01-01");
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> shelfLayerService.delete(blocked.getId()));
        assertTrue(ex.getMessage().contains("封锁"));
        assertNotNull(shelfLayerMapper.selectById(blocked.getId()));
    }

    // ---------------- 解除封锁 ----------------

    /** 解除封锁必须填写结论；解除后层位恢复可上架，且禁止重复解除 */
    @Test
    void release_requiresConclusion_andRestoresLayer() {
        layer("A-01-01", 2);
        LayerBlockRecord record = layerBlockService.block(blockDto("A-01-01"));

        // 空结论：拒绝
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> layerBlockService.release(releaseDto(record.getId(), "  ")));
        assertTrue(ex.getMessage().contains("结论"));
        assertEquals("BLOCKED", blockRecordMapper.selectById(record.getId()).getStatus());

        // 填写结论：解除成功，记录解除时间/结论/经办人
        LayerBlockReleaseDTO dto = releaseDto(record.getId(), "破损层板已更换，恢复使用");
        dto.setReleaseOperator("赵班长");
        LayerBlockRecord released = layerBlockService.release(dto);
        assertEquals("RELEASED", released.getStatus());
        assertEquals("破损层板已更换，恢复使用", released.getReleaseConclusion());
        assertEquals("赵班长", released.getReleaseOperator());
        assertNotNull(released.getReleaseTime());

        // 重复解除：拒绝
        assertThrows(RuntimeException.class,
                () -> layerBlockService.release(releaseDto(record.getId(), "再次解除")));

        // 层位恢复可绑定，占用数同步刷新
        assertNull(shelfLayerService.getByCode("A-01-01").getActiveBlock());
        Long padId = padOnShelf("P-001", null);
        BindLayerDTO bind = new BindLayerDTO();
        bind.setPadId(padId);
        bind.setLayerCode("A-01-01");
        padInfoService.bindLayer(bind);
        assertEquals(1, shelfLayerService.getByCode("A-01-01").getPadCount());
    }

    /** 解除时间不能早于封锁开始时间 */
    @Test
    void release_beforeStartTime_rejected() {
        layer("A-01-01", 5);
        LayerBlockDTO block = blockDto("A-01-01");
        block.setStartTime(LocalDateTime.now().minusHours(2));
        LayerBlockRecord record = layerBlockService.block(block);

        LayerBlockReleaseDTO dto = releaseDto(record.getId(), "检修完成");
        dto.setReleaseTime(LocalDateTime.now().minusHours(5));
        assertThrows(RuntimeException.class, () -> layerBlockService.release(dto));
        assertEquals("BLOCKED", blockRecordMapper.selectById(record.getId()).getStatus());
    }

    // ---------------- 台账回看 ----------------

    /** 台账按封锁状态与开始日期区间筛选 */
    @Test
    void pageList_filtersByStatusAndDateRange() {
        layer("A-01-01", 5);
        layer("A-01-02", 5);
        layer("B-01-01", 5);

        LayerBlockDTO b1 = blockDto("A-01-01");
        b1.setStartTime(LocalDateTime.now().minusDays(10));
        LayerBlockRecord r1 = layerBlockService.block(b1);

        LayerBlockDTO b2 = blockDto("A-01-02");
        b2.setBlockType("CLEANING");
        b2.setStartTime(LocalDateTime.now().minusDays(2));
        layerBlockService.block(b2);

        LayerBlockDTO b3 = blockDto("B-01-01");
        b3.setStartTime(LocalDateTime.now());
        layerBlockService.block(b3);
        layerBlockService.release(releaseDto(
                blockRecordMapper.selectList(new LambdaQueryWrapper<LayerBlockRecord>()
                                .eq(LayerBlockRecord::getLayerCode, "B-01-01"))
                        .get(0).getId(),
                "清扫完成，恢复使用"));

        // 按状态筛选：封锁中 2 条，已解除 1 条
        LayerBlockRecordQueryDTO blockedQuery = new LayerBlockRecordQueryDTO();
        blockedQuery.setStatus("BLOCKED");
        Page<LayerBlockRecord> blockedPage = layerBlockService.pageList(blockedQuery);
        assertEquals(2, blockedPage.getTotal());

        LayerBlockRecordQueryDTO releasedQuery = new LayerBlockRecordQueryDTO();
        releasedQuery.setStatus("RELEASED");
        Page<LayerBlockRecord> releasedPage = layerBlockService.pageList(releasedQuery);
        assertEquals(1, releasedPage.getTotal());
        assertEquals("清扫完成，恢复使用", releasedPage.getRecords().get(0).getReleaseConclusion());

        // 按日期区间筛选：最近 3 天内 2 条
        LayerBlockRecordQueryDTO dateQuery = new LayerBlockRecordQueryDTO();
        dateQuery.setStartTime(LocalDateTime.now().minusDays(3));
        Page<LayerBlockRecord> datePage = layerBlockService.pageList(dateQuery);
        assertEquals(2, datePage.getTotal());

        // 按层位编码 + 类型组合筛选
        LayerBlockRecordQueryDTO combineQuery = new LayerBlockRecordQueryDTO();
        combineQuery.setLayerCode("A-01-02");
        combineQuery.setBlockType("CLEANING");
        Page<LayerBlockRecord> combinePage = layerBlockService.pageList(combineQuery);
        assertEquals(1, combinePage.getTotal());
        assertEquals("A-01-02", combinePage.getRecords().get(0).getLayerCode());

        // 统计：封锁中/已解除/本月
        assertEquals(2L, layerBlockService.statistics().get("blockedCount"));
        assertEquals(1L, layerBlockService.statistics().get("releasedCount"));
        assertEquals(3L, layerBlockService.statistics().get("monthBlockCount"));

        // 解除后 r1 仍封锁中，层位 A-01-01 不可上架；B-01-01 已恢复
        assertNotNull(shelfLayerService.getByCode("A-01-01").getActiveBlock());
        assertNull(shelfLayerService.getByCode("B-01-01").getActiveBlock());
        assertNotNull(r1.getId());
    }
}
