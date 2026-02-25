package com.hnieacm.announcement.auth;

import cn.dev33.satoken.stp.StpInterface;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.announcement.feign.AuthInternalFeignClient;
import com.hnieacm.common.constant.AuthCacheConstant;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/24
 * @Description: Sa-Token 权限/角色查询实现
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
