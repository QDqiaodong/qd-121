package com.stamping.pad.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PadSpecCacheService {

    private static final String CACHE_KEY = "pad:spec:templates";

    private final RedisTemplate<String, Object> redisTemplate;

    private HashOperations<String, String, Object> hashOperations;

    @PostConstruct
    public void init() {
        this.hashOperations = redisTemplate.opsForHash();
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(CACHE_KEY))) {
            initDefaultSpecs();
        }
    }

    private void initDefaultSpecs() {
        Map<String, Object> defaultSpecs = new HashMap<>();
        defaultSpecs.put("heavy_large", "{\"name\":\"重型大垫板\",\"length\":2000,\"width\":1500,\"thickness\":50}");
        defaultSpecs.put("heavy_medium", "{\"name\":\"重型中垫板\",\"length\":1500,\"width\":1000,\"thickness\":40}");
        defaultSpecs.put("heavy_small", "{\"name\":\"重型小垫板\",\"length\":1000,\"width\":800,\"thickness\":30}");
        defaultSpecs.put("medium_large", "{\"name\":\"中型大垫板\",\"length\":1200,\"width\":800,\"thickness\":25}");
        defaultSpecs.put("medium_medium", "{\"name\":\"中型中垫板\",\"length\":800,\"width\":600,\"thickness\":20}");
        defaultSpecs.put("medium_small", "{\"name\":\"中型小垫板\",\"length\":600,\"width\":400,\"thickness\":15}");
        defaultSpecs.put("light_large", "{\"name\":\"轻型大垫板\",\"length\":500,\"width\":400,\"thickness\":12}");
        defaultSpecs.put("light_medium", "{\"name\":\"轻型中垫板\",\"length\":400,\"width\":300,\"thickness\":10}");
        defaultSpecs.put("light_small", "{\"name\":\"轻型小垫板\",\"length\":300,\"width\":200,\"thickness\":8}");
        defaultSpecs.put("special_custom", "{\"name\":\"定制特殊垫板\",\"length\":0,\"width\":0,\"thickness\":0}");
        hashOperations.putAll(CACHE_KEY, defaultSpecs);
    }

    public Map<String, Object> getAllSpecs() {
        return hashOperations.entries(CACHE_KEY);
    }

    public Object getSpec(String key) {
        return hashOperations.get(CACHE_KEY, key);
    }

    public void addSpec(String key, String value) {
        hashOperations.put(CACHE_KEY, key, value);
    }

    public void deleteSpec(String key) {
        hashOperations.delete(CACHE_KEY, key);
    }

    public Set<String> getAllKeys() {
        return hashOperations.keys(CACHE_KEY);
    }
}
