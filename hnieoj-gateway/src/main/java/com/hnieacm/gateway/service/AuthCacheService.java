package com.hnieacm.gateway.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.constant.AuthCacheConstant;
import com.hnieacm.gateway.properties.AuthCacheTtlProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PreDestroy;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/13
 * @Description: 网关授权的角色/权限缓存
 * @Attention: 网关授权运行在 Reactor 事件循环线程上，在此路径中请勿使用响应式的 block() 方法。
 *             此服务使用同步的 Redis 访问方式
 */
@Slf4j
@Service
@RefreshScope
@RequiredArgsConstructor
public class AuthCacheService {

    private static final long CACHE_ACCESS_TIMEOUT_MS = 200;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ExecutorService cacheExecutor = Executors.newFixedThreadPool(4);
    private final Map<String, LocalCacheValue> localFallbackCache = new ConcurrentHashMap<>();

    // roles/permissions 缓存 TTL
    private final AuthCacheTtlProperties authCacheTtlProperties;

    /**
     * @MethodName getList
     * @Param cacheKey
     * @Param uid
     * @Param dataType
     * @Description 获取列表
     * @Return @return {@link List }<{@link String }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    public List<String> getList(String cacheKey, String uid, String dataType) {
        Future<CacheReadResult> future = cacheExecutor.submit(() -> getListDirect(cacheKey, uid, dataType));
        try {
            CacheReadResult result = future.get(CACHE_ACCESS_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (result.hit()) {
                refreshLocalFallback(cacheKey, result.data());
                return result.data();
            }
            return getLocalFallback(cacheKey, uid, dataType);
        } catch (Exception e) {
            future.cancel(true);
            log.warn("Read auth cache timeout or failed, type: {}, uid: {}", dataType, uid, e);
            return getLocalFallback(cacheKey, uid, dataType);
        }
    }

    private CacheReadResult getListDirect(String cacheKey, String uid, String dataType) {
        try {
            String json = stringRedisTemplate.opsForValue().get(cacheKey);
            if (json == null || json.isBlank()) {
                return new CacheReadResult(false, Collections.emptyList());
            }

            List<String> data = objectMapper.readValue(json, new TypeReference<>() {
            });
            if (data == null) {
                data = Collections.emptyList();
            }

            if (!data.isEmpty()) {
                stringRedisTemplate.expire(cacheKey, resolveTtl(dataType));
            }

            log.debug("Auth cache hit, type: {}, uid: {}, size: {}", dataType, uid, data.size());
            return new CacheReadResult(true, data);
        } catch (Exception e) {
            log.warn("Read auth cache failed, type: {}, uid: {}", dataType, uid, e);
            return new CacheReadResult(false, Collections.emptyList());
        }
    }

    @PreDestroy
    public void shutdown() {
        cacheExecutor.shutdownNow();
    }

    /**
     * @MethodName setList
     * @Param cacheKey
     * @Param data
     * @Param uid
     * @Param dataType
     * @Description 设置列表
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    public void setList(String cacheKey, List<String> data, String uid, String dataType) {
        List<String> safeData = data == null ? Collections.emptyList() : data;
        Duration ttl = safeData.isEmpty() ? resolveEmptyTtl() : resolveTtl(dataType);
        try {
            String json = objectMapper.writeValueAsString(safeData);
            stringRedisTemplate.opsForValue().set(cacheKey, json, ttl);
            log.debug("Auth cache set, type: {}, uid: {}, size: {}, ttl: {}", dataType, uid, safeData.size(), ttl);
        } catch (Exception e) {
            log.warn("Write auth cache failed, type: {}, uid: {}", dataType, uid, e);
        }
    }

    /**
     * @MethodName resolveTtl
     * @Param dataType
     * @Description 解析 TTL
     * @Return @return {@link Duration }
     * @Author HaoRan_Lyu
     * @Date 2026/02/14
     */
    private Duration resolveTtl(String dataType) {
        if (AuthCacheConstant.ROLE_CACHE_TYPE.equals(dataType)) {
            return withJitter(authCacheTtlProperties.getRoles());
        }
        if (AuthCacheConstant.PERMISSION_CACHE_TYPE.equals(dataType)) {
            return withJitter(authCacheTtlProperties.getPermissions());
        }
        throw new IllegalArgumentException("Unsupported auth cache data type: " + dataType);
    }

    private Duration resolveEmptyTtl() {
        Duration emptyTtl = authCacheTtlProperties.getEmpty();
        return withJitter(emptyTtl == null ? Duration.ofMinutes(5) : emptyTtl);
    }

    private Duration withJitter(Duration baseTtl) {
        Duration jitter = authCacheTtlProperties.getJitter();
        if (jitter == null || jitter.isZero() || jitter.isNegative()) {
            return baseTtl;
        }
        long jitterMillis = jitter.toMillis();
        if (jitterMillis <= 0) {
            return baseTtl;
        }
        return baseTtl.plusMillis(ThreadLocalRandom.current().nextLong(jitterMillis + 1));
    }

    private void refreshLocalFallback(String cacheKey, List<String> data) {
        Duration ttl = authCacheTtlProperties.getLocalFallback();
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            localFallbackCache.remove(cacheKey);
            return;
        }
        localFallbackCache.put(cacheKey, new LocalCacheValue(
                data == null ? Collections.emptyList() : List.copyOf(data),
                System.currentTimeMillis() + ttl.toMillis()
        ));
    }

    private List<String> getLocalFallback(String cacheKey, String uid, String dataType) {
        LocalCacheValue value = localFallbackCache.get(cacheKey);
        if (value == null) {
            return Collections.emptyList();
        }
        if (value.expireAtMillis() < System.currentTimeMillis()) {
            localFallbackCache.remove(cacheKey);
            return Collections.emptyList();
        }
        log.warn("Use local auth cache fallback, type: {}, uid: {}", dataType, uid);
        return value.data();
    }

    private record CacheReadResult(boolean hit, List<String> data) {
    }

    private record LocalCacheValue(List<String> data, long expireAtMillis) {
    }
}
