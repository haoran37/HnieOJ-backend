package com.hnieacm.auth.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.auth.service.AuthPermissionService;
import com.hnieacm.auth.service.UserAuthCacheService;
import com.hnieacm.common.constant.AuthCacheConstant;
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
 * @Description: 用户鉴权信息缓存实现。
 */
@Slf4j
@Service
@RefreshScope
@RequiredArgsConstructor
public class UserAuthCacheServiceImpl implements UserAuthCacheService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AuthPermissionService authPermissionService;

    @Value("${hnieoj.auth-cache.ttl-minutes:}")
    private long authCacheTtlMinutes;

    /**
     * @MethodName cacheUserAuth
     * @Param uid
     * @Description 缓存用户身份验证
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    @Override
    public void cacheUserAuth(String uid) {
        if (uid == null || uid.isBlank()) {
            return;
        }

        try {
            List<String> roles = authPermissionService.getUserRoles(uid);
            List<String> permissions = authPermissionService.getUserPermissions(uid);

            setList(AuthCacheConstant.ROLE_CACHE_PREFIX + uid, roles);
            setList(AuthCacheConstant.PERMISSION_CACHE_PREFIX + uid, permissions);

            log.debug("Cache user auth success, uid: {}, roles: {}, permissionsSize: {}",
                    uid, roles, permissions == null ? 0 : permissions.size());
        } catch (Exception e) {
            log.warn("Cache user auth failed, uid: {}", uid, e);
        }
    }

    /**
     * @MethodName setList
     * @Param key
     * @Param list
     * @Description 设置列表
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    private void setList(String key, List<String> list) throws Exception {
        List<String> safeList = list == null ? Collections.emptyList() : list;
        Duration finalTtl = safeList.isEmpty() ? Duration.ofMinutes(5) : getAuthCacheTtl();
        String json = objectMapper.writeValueAsString(safeList);
        stringRedisTemplate.opsForValue().set(key, json, finalTtl);
    }

    /**
     * @MethodName getAuthCacheTtl
     *
     * @Description 获取身份验证缓存ttl
     * @Return @return {@link Duration }
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    private Duration getAuthCacheTtl() {
        return Duration.ofMinutes(authCacheTtlMinutes);
    }
}
