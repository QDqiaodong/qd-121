package com.stamping.pad;

import com.stamping.pad.dto.LayerBlockDTO;
import com.stamping.pad.entity.LayerBlockRecord;
import com.stamping.pad.entity.ShelfLayer;
import com.stamping.pad.mapper.LayerBlockRecordMapper;
import com.stamping.pad.mapper.ShelfLayerMapper;
import com.stamping.pad.service.LayerBlockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 层位封锁台账 Web 层绑定验证：GET 查询参数中的状态与日期区间
 * （yyyy-MM-dd HH:mm:ss，与前端日期选择器格式一致）能正确绑定并过滤。
 */
@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
class LayerBlockWebBindTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private LayerBlockService layerBlockService;
    @Autowired
    private LayerBlockRecordMapper blockRecordMapper;
    @Autowired
    private ShelfLayerMapper shelfLayerMapper;

    @BeforeEach
    void setUp() {
        blockRecordMapper.delete(null);
        shelfLayerMapper.delete(null);

        ShelfLayer layer = new ShelfLayer();
        layer.setLayerCode("A-01-01");
        layer.setShelfCode("A-01");
        layer.setLayerName("A区01货架第1层");
        layer.setLayerOrder(1);
        layer.setCapacity(10);
        shelfLayerMapper.insert(layer);

        LayerBlockDTO dto = new LayerBlockDTO();
        dto.setLayerCode("A-01-01");
        dto.setBlockType("DAMAGE");
        dto.setBlockReason("层板变形破损");
        dto.setOperator("王库管");
        dto.setStartTime(LocalDateTime.of(2026, 9, 10, 8, 30, 0));
        layerBlockService.block(dto);
    }

    /** 按封锁状态筛选：BLOCKED 命中，RELEASED 为空 */
    @Test
    void page_filterByStatus() throws Exception {
        mockMvc.perform(get("/layer-block/page").param("status", "BLOCKED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].layerCode").value("A-01-01"))
                .andExpect(jsonPath("$.data.records[0].operator").value("王库管"));

        mockMvc.perform(get("/layer-block/page").param("status", "RELEASED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    /** 按日期区间筛选：yyyy-MM-dd HH:mm:ss 查询参数能正确绑定 LocalDateTime */
    @Test
    void page_filterByDateRange() throws Exception {
        // 覆盖封锁开始时间的区间：命中
        mockMvc.perform(get("/layer-block/page")
                        .param("startTime", "2026-09-01 00:00:00")
                        .param("endTime", "2026-09-11 00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        // 开始时间之后的区间：无结果（而不是 400 绑定失败）
        mockMvc.perform(get("/layer-block/page")
                        .param("startTime", "2026-09-11 00:00:00")
                        .param("endTime", "2026-09-12 00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    /** 层位编码模糊 + 类型组合筛选 */
    @Test
    void page_filterByLayerCodeAndType() throws Exception {
        mockMvc.perform(get("/layer-block/page")
                        .param("layerCode", "A-01")
                        .param("blockType", "DAMAGE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value(LayerBlockRecord.STATUS_BLOCKED));

        mockMvc.perform(get("/layer-block/page")
                        .param("layerCode", "A-01")
                        .param("blockType", "CLEANING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }
}
