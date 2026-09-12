package com.stamping.pad;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stamping.pad.entity.PadInfo;
import com.stamping.pad.entity.ShelfLayer;
import com.stamping.pad.mapper.PadBorrowRecordMapper;
import com.stamping.pad.mapper.PadInfoMapper;
import com.stamping.pad.mapper.ShelfLayerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 领用归还逾期闭环（真实 HTTP/Jackson 链路）：
 * 1) 前端 yyyy-MM-dd HH:mm:ss 文本提交的预计归还时间能入库（修复前直接 500）；
 * 2) 预计归还时间过后，台账动态标记 overdue、OVERDUE 筛选命中、统计 overdueCount 增长；
 * 3) 档案分页接口带出 borrowOverdue/expectedReturnTime（档案页可标逾期催还）；
 * 4) 未到期不误判；归还后逾期计数清零。
 */
@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
class BorrowOverdueWebTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PadInfoMapper padInfoMapper;
    @Autowired private ShelfLayerMapper shelfLayerMapper;
    @Autowired private PadBorrowRecordMapper borrowMapper;

    private Long padId;

    @BeforeEach
    void setUp() {
        borrowMapper.delete(null);
        padInfoMapper.delete(null);
        shelfLayerMapper.delete(null);

        layer("A-01-02");
        layer("A-01-01");

        PadInfo p = new PadInfo();
        p.setPadCode("P-OW-1");
        p.setMoldType("模具X");
        p.setLength(new BigDecimal("1000"));
        p.setWidth(new BigDecimal("800"));
        p.setThickness(new BigDecimal("20"));
        p.setShelfLayerCode("A-01-01");
        p.setMaintenanceStatus("AVAILABLE");
        p.setBindTime(LocalDateTime.now().minusDays(5));
        p.setCreateTime(LocalDateTime.now().minusDays(5));
        p.setUpdateTime(LocalDateTime.now());
        padInfoMapper.insert(p);
        padId = p.getId();
    }

    private Long layer(String code) {
        ShelfLayer l = new ShelfLayer();
        l.setLayerCode(code);
        l.setShelfCode("A-01");
        l.setLayerName(code + "层");
        l.setLayerOrder(1);
        l.setCapacity(10);
        shelfLayerMapper.insert(l);
        return l.getId();
    }

    private String checkoutBody(String expectedReturnTime) {
        String expectedJson = expectedReturnTime == null
                ? "null"
                : "\"" + expectedReturnTime + "\"";
        return """
            {
              "padId": %d,
              "borrower": "张三",
              "productionLine": "冲压一线-1号工位",
              "purpose": "换模",
              "checkoutTime": "%s",
              "expectedReturnTime": %s
            }
            """.formatted(padId,
                LocalDateTime.now().minusDays(3).format(FMT),
                expectedJson);
    }

    private long checkout(String expectedReturnTime) throws Exception {
        MvcResult res = mockMvc.perform(post("/borrow/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody(expectedReturnTime)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("BORROWED"))
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    @Test
    void checkoutWithPastExpectedReturn_marksOverdueEverywhere() throws Exception {
        String past = LocalDateTime.now().minusDays(1).format(FMT);

        // 修复前：带空格时间的 body 直接 500，且响应不回显预计归还时间
        mockMvc.perform(post("/borrow/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody(past)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BORROWED"))
                .andExpect(jsonPath("$.data.expectedReturnTime").value(past));

        // 台账列表动态逾期标记
        mockMvc.perform(get("/borrow/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value("BORROWED"))
                .andExpect(jsonPath("$.data.records[0].overdue").value(true));

        // 逾期筛选命中
        mockMvc.perform(get("/borrow/page").param("status", "OVERDUE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        // 领用中筛选也能看到这条（逾期是领用中的子状态）
        mockMvc.perform(get("/borrow/page").param("status", "BORROWED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        // 统计：领用中 1、逾期 1
        mockMvc.perform(get("/borrow/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.borrowedCount").value(1))
                .andExpect(jsonPath("$.data.overdueCount").value(1));

        // 档案分页接口带出逾期标记与预计归还时间（档案页据此标红催还）
        mockMvc.perform(get("/pad/page").param("pageSize", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].borrowStatus").value("BORROWED"))
                .andExpect(jsonPath("$.data.records[0].borrowOverdue").value(true))
                .andExpect(jsonPath("$.data.records[0].expectedReturnTime").value(past));
    }

    @Test
    void notYetDue_isNotOverdue() throws Exception {
        String future = LocalDateTime.now().plusDays(2).format(FMT);
        checkout(future);

        mockMvc.perform(get("/borrow/page"))
                .andExpect(jsonPath("$.data.records[0].overdue").value(false));
        mockMvc.perform(get("/borrow/page").param("status", "OVERDUE"))
                .andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/borrow/statistics"))
                .andExpect(jsonPath("$.data.borrowedCount").value(1))
                .andExpect(jsonPath("$.data.overdueCount").value(0));
    }

    @Test
    void noExpectedReturn_isNotOverdue() throws Exception {
        checkout(null);

        mockMvc.perform(get("/borrow/page"))
                .andExpect(jsonPath("$.data.records[0].overdue").value(false));
        mockMvc.perform(get("/borrow/statistics"))
                .andExpect(jsonPath("$.data.overdueCount").value(0));
    }

    @Test
    void returnAfterOverdue_clearsOverdueCount() throws Exception {
        String past = LocalDateTime.now().minusDays(1).format(FMT);
        long recordId = checkout(past);

        // 归还到空闲层 A-01-02
        String returnBody = """
            {"id": %d, "returnLayerCode": "A-01-02", "returnTime": "%s"}
            """.formatted(recordId, LocalDateTime.now().format(FMT));
        mockMvc.perform(post("/borrow/return")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RETURNED"));

        // 归还后不再计入领用中/逾期
        mockMvc.perform(get("/borrow/statistics"))
                .andExpect(jsonPath("$.data.borrowedCount").value(0))
                .andExpect(jsonPath("$.data.overdueCount").value(0))
                .andExpect(jsonPath("$.data.returnedCount").value(1));
        mockMvc.perform(get("/borrow/page").param("status", "OVERDUE"))
                .andExpect(jsonPath("$.data.total").value(0));

        // 档案页逾期标记随之清除
        mockMvc.perform(get("/pad/page").param("pageSize", "100"))
                .andExpect(jsonPath("$.data.records[0].borrowOverdue").value(false));
    }
}
