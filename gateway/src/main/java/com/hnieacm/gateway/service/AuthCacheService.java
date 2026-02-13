package com.hnieacm.gateway.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

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

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;


    // roles/permissions 缓存 TTL
    @Value("${hnieoj.auth-cache.ttl-minutes:}")
    private long authCacheTtlMinutes;

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
        try {
            String json = stringRedisTemplate.opsForValue().get(cacheKey);
            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }

            List<String> data = objectMapper.readValue(json, new TypeReference<>() {
            });
            if (data == null) {
                return Collections.emptyList();
            }

            if (!data.isEmpty()) {
                stringRedisTemplate.expire(cacheKey, Duration.ofMinutes(authCacheTtlMinutes));
            }

            log.debug("Auth cache hit, type: {}, uid: {}, size: {}", dataType, uid, data.size());
            return data;
        } catch (Exception e) {
            log.warn("Read auth cache failed, type: {}, uid: {}", dataType, uid, e);
            return Collections.emptyList();
        }
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
        Duration ttl = safeData.isEmpty() ? Duration.ofMinutes(5) : Duration.ofMinutes(authCacheTtlMinutes);
        try {
            String json = objectMapper.writeValueAsString(safeData);
            stringRedisTemplate.opsForValue().set(cacheKey, json, ttl);
            log.debug("Auth cache set, type: {}, uid: {}, size: {}, ttl: {}", dataType, uid, safeData.size(), ttl);
        } catch (Exception e) {
            log.warn("Write auth cache failed, type: {}, uid: {}", dataType, uid, e);
        }
    }
}
