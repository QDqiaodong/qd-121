package com.stamping.pad.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;

/**
 * JSR-310 时间类型（LocalDateTime/LocalDate/LocalTime）统一按
 * yyyy-MM-dd HH:mm:ss 与前端交互。
 *
 * 注意：spring.jackson.date-format 只对传统 java.util.Date 生效，
 * 对 java.time.LocalDateTime 无效——后者默认仅接受 ISO（yyyy-MM-ddTHH:mm:ss）。
 * 前端日期组件统一产出 yyyy-MM-dd HH:mm:ss 文本并以 JSON body 提交，
 * 不显式配置反序列化器会在领用/归还/保养/报废/封锁/扩容/预留等所有带时间的
 * 登记接口上抛 DateTimeParseException（HTTP 500），导致预计归还时间等无法入库。
 * 这里同时配置序列化与反序列化，保证请求/响应时间格式一致。
 */
@Configuration
public class JacksonConfig {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final String TIME_PATTERN = "HH:mm:ss";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsr310FormatCustomizer() {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DATE_PATTERN);
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(TIME_PATTERN);
        return builder -> builder
                .serializers(new LocalDateTimeSerializer(dateTimeFormatter))
                .deserializers(new LocalDateTimeDeserializer(dateTimeFormatter))
                .serializers(new LocalDateSerializer(dateFormatter))
                .deserializers(new LocalDateDeserializer(dateFormatter))
                .serializers(new LocalTimeSerializer(timeFormatter))
                .deserializers(new LocalTimeDeserializer(timeFormatter));
    }
}
