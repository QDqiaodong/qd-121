package com.stamping.pad;

import org.mockito.Mockito;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * 测试专用启动类：
 * - 不真实连接 Redis，用 Mockito 提供 RedisConnectionFactory；
 *   hasKey 返回 true 使规格模板缓存跳过初始化写入，对被测筛选/领用链路无影响。
 * - 其余组件扫描与生产保持一致。
 */
@org.springframework.boot.SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class
})
@ComponentScan(basePackages = "com.stamping.pad")
public class TestApplication {

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisConnectionFactory factory = Mockito.mock(RedisConnectionFactory.class);
        RedisConnection connection = Mockito.mock(RedisConnection.class, RETURNS_DEEP_STUBS);
        Mockito.when(factory.getConnection()).thenReturn(connection);
        // key 已存在 -> PadSpecCacheService.init() 不再执行 putAll 写入
        Mockito.when(connection.exists(Mockito.any(byte[].class))).thenReturn(true);
        return factory;
    }
}
