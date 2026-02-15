package com.hnieacm.user.auth;

import cn.dev33.satoken.stp.StpInterface;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.constant.AuthCacheConstant;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.user.feign.AuthInternalFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: Sa-Token 权限/角色查询实现
 * <p>
 * 优先读取认证服务写入的 Redis 缓存，缓存缺失时触发一次内部刷新，再次读取
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private static final TypeReference<List<String>> LIST_STRING_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AuthInternalFeignClient authInternalFeignClient;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        String uid = String.valueOf(loginId);
        // permission 允许为空，但缓存缺失时仍需触发刷新，避免误判无权限
        return readOrRefresh(AuthCacheConstant.PERMISSION_CACHE_PREFIX + uid, uid, true);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        String uid = String.valueOf(loginId);
        List<String> roles = readOrRefresh(AuthCacheConstant.ROLE_CACHE_PREFIX + uid, uid, false);
        return roles.isEmpty() ? List.of(RoleConstant.STUDENT) : roles;
    }

    private List<String> readOrRefresh(String key, String uid, boolean allowEmpty) {
        // 先读缓存：空集合可能是有效值（如学生无额外权限），但 key 不存在需要触发刷新
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null && !json.isBlank()) {
            List<String> list = parseJsonList(key, json);
            if (allowEmpty || !list.isEmpty()) {
                return list;
            }
        }

        // 缓存缺失/空角色：触发 auth 内部刷新后再读取一次
        refreshAuthCache(uid);

        json = stringRedisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        return parseJsonList(key, json);
    }

    private void refreshAuthCache(String uid) {
        try {
            var result = authInternalFeignClient.refreshUserAuthCache(uid);
            if (result == null || result.getCode() != ResultCode.SUCCESS) {
                log.warn("Auth cache refresh failed, uid: {}, result: {}", uid, result);
            }
        } catch (Exception e) {
            log.warn("Auth cache refresh failed, uid: {}", uid, e);
        }
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
