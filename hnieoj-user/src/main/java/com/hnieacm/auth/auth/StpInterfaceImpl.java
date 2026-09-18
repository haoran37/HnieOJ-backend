package com.hnieacm.auth.auth;

import cn.dev33.satoken.stp.StpInterface;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.auth.service.UserAuthCacheService;
import com.hnieacm.common.constant.AuthCacheConstant;
import com.hnieacm.common.constant.RoleConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: Sa-Token 权限/角色查询实现，优先读取 auth 服务写入的 Redis 缓存，
 * 缓存 miss 时触发本服务再生成 roles/permissions 缓存，避免重复打应 DB
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private static final TypeReference<List<String>> LIST_STRING_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final UserAuthCacheService userAuthCacheService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        String uid = String.valueOf(loginId);
        // permission 允许为空，但 key 缺失时仍需触发刷新，避免误判无权限
        return readOrRefresh(AuthCacheConstant.PERMISSION_CACHE_PREFIX + uid, uid, true);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        String uid = String.valueOf(loginId);
        List<String> roles = readOrRefresh(AuthCacheConstant.ROLE_CACHE_PREFIX + uid, uid, false);
        return roles.isEmpty() ? List.of(RoleConstant.STUDENT) : roles;
    }

    private List<String> readOrRefresh(String key, String uid, boolean allowEmpty) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null && !json.isBlank()) {
            List<String> list = parseJsonList(key, json);
            if (allowEmpty || !list.isEmpty()) {
                return list;
            }
        }

        // 缓存 miss/空角色：再生成缓存后读一次
        try {
            userAuthCacheService.cacheUserAuth(uid);
        } catch (Exception e) {
            log.debug("Cache user auth ignored, uid: {}, msg: {}", uid, e.getMessage());
        }

        json = stringRedisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        return parseJsonList(key, json);
    }

    private List<String> parseJsonList(String key, String json) {
        try {
            List<String> list = objectMapper.readValue(json, LIST_STRING_TYPE);
            return list == null ? Collections.emptyList() : list;
        } catch (Exception e) {
            log.warn("Read auth cache failed, key: {}", key, e);
            return Collections.emptyList();
        }
    }
}

