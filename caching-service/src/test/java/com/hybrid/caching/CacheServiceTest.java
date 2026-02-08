package com.hybrid.caching;

import com.hybrid.caching.service.CacheService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CacheServiceTest {

    private InMemoryRedisTemplate redisTemplate;
    private CacheService cacheService;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        redisTemplate = new InMemoryRedisTemplate();
        meterRegistry = new SimpleMeterRegistry();
        cacheService = new CacheService(redisTemplate, meterRegistry);
    }

    @Test
    void testPut() {
        cacheService.put("key", "value", 60);
        assertEquals("value", redisTemplate.backingStore.get("key"));
    }

    @Test
    void testGet() {
        redisTemplate.backingStore.put("key", "cachedValue");
        Object result = cacheService.get("key");
        assertEquals("cachedValue", result);
        assertEquals(1.0, meterRegistry.counter("cache_hit_count_total").count());
    }

    @Test
    void testGetMiss() {
        Object result = cacheService.get("missing-key");
        assertNull(result);
        assertEquals(1.0, meterRegistry.counter("cache_miss_count_total").count());
    }

    @Test
    void testEvict() {
        redisTemplate.backingStore.put("key", "value");
        cacheService.evict("key");
        assertFalse(redisTemplate.backingStore.containsKey("key"));
    }

    private static class InMemoryRedisTemplate extends RedisTemplate<String, Object> {
        private final Map<String, Object> backingStore = new ConcurrentHashMap<>();
        private final ValueOperations<String, Object> valueOps = createValueOps(backingStore);

        @Override
        public ValueOperations<String, Object> opsForValue() {
            return valueOps;
        }

        @Override
        public Boolean delete(String key) {
            return backingStore.remove(key) != null;
        }

        @SuppressWarnings("unchecked")
        private static ValueOperations<String, Object> createValueOps(Map<String, Object> store) {
            return (ValueOperations<String, Object>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(),
                    new Class[]{ValueOperations.class},
                    (proxy, method, args) -> {
                        String methodName = method.getName();
                        if ("set".equals(methodName) && args != null && args.length >= 2) {
                            store.put((String) args[0], args[1]);
                            return null;
                        }
                        if ("get".equals(methodName) && args != null && args.length == 1) {
                            return store.get(args[0]);
                        }
                        throw new UnsupportedOperationException("Unsupported method in test double: " + methodName);
                    }
            );
        }
    }
}
