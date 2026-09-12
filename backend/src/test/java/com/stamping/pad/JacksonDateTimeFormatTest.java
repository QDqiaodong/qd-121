package com.stamping.pad;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stamping.pad.dto.PadCheckoutDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 验证 JacksonConfig 生效：前端 yyyy-MM-dd HH:mm:ss 文本可被 LocalDateTime 正确解析。
 * 仅 spring.jackson.date-format 时 JSR-310 默认只接受 ISO，会导致登记接口 500。
 */
@SpringBootTest(classes = TestApplication.class)
class JacksonDateTimeFormatTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void parseLocalDateTime_withSpacePattern() throws Exception {
        String json = "{\"padId\":1,\"borrower\":\"x\",\"productionLine\":\"y\","
                + "\"checkoutTime\":\"2026-09-09 08:00:00\","
                + "\"expectedReturnTime\":\"2026-09-11 18:30:00\"}";
        PadCheckoutDTO dto = objectMapper.readValue(json, PadCheckoutDTO.class);
        assertNotNull(dto.getCheckoutTime());
        assertNotNull(dto.getExpectedReturnTime());
        assertEquals(2026, dto.getExpectedReturnTime().getYear());
        assertEquals(9, dto.getExpectedReturnTime().getMonthValue());
        assertEquals(11, dto.getExpectedReturnTime().getDayOfMonth());
        assertEquals(18, dto.getExpectedReturnTime().getHour());
        assertEquals(30, dto.getExpectedReturnTime().getMinute());

        // 序列化也统一为空格格式（无 ISO 的 T）
        String out = objectMapper.writeValueAsString(dto);
        org.junit.jupiter.api.Assertions.assertTrue(out.contains("2026-09-11 18:30:00"));
        org.junit.jupiter.api.Assertions.assertFalse(out.contains("T18:30"));
    }
}
