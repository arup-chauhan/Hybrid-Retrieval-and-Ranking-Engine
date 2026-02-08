
package com.hybrid.caching.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Service
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;

    public CacheService(RedisTemplate<String, Object> redisTemplate, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
    }

    public void put(String key, Object value, long ttlSeconds) {
        redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
    }

    public Object get(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            meterRegistry.counter("cache_miss_count_total").increment();
        } else {
            meterRegistry.counter("cache_hit_count_total").increment();
        }
        return value;
    }

    public void evict(String key) {
        redisTemplate.delete(key);
    }
}
