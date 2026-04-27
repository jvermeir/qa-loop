package com.example.demo.infrastructure;

import java.util.HashMap;
import java.util.Map;

public class CacheManager {

    // S2696: non-thread-safe mutable static state (should be ConcurrentHashMap)
    private static Map<String, Object> cache = new HashMap<>();
    private static int hitCount = 0;
    private static int missCount = 0;

    // S2886: unsynchronized — race condition under concurrent access
    public static void put(String key, Object value) {
        cache.put(key, value);
    }

    // S2886: unsynchronized — hitCount/missCount updates are not atomic
    public static Object get(String key) {
        Object value = cache.get(key);
        if (value != null) {
            hitCount++;
        } else {
            missCount++;
        }
        return value;
    }

    public static void clear() {
        cache.clear();
        hitCount = 0;
        missCount = 0;
    }

    public static String getStats() {
        return "hits=" + hitCount + ", misses=" + missCount + ", size=" + cache.size();
    }

    // S2065: transient field in a non-Serializable class is meaningless
    private transient String lastAccessedKey;

    public void recordAccess(String key) {
        lastAccessedKey = key;
    }
}
