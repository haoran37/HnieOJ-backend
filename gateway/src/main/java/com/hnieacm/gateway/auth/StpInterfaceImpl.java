package com.hnieacm.gateway.auth;

import cn.dev33.satoken.stp.StpInterface;
import com.hnieacm.common.constant.AuthCacheConstant;
import com.hnieacm.gateway.service.AuthCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/13
 * @Description: Sa-Token permission provider for gateway authorization
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final AuthCacheService authCacheService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        String uid = String.valueOf(loginId);
        String cacheKey = AuthCacheConstant.PERMISSION_CACHE_PREFIX + uid;
        List<String> cached = authCacheService.getList(cacheKey, uid, AuthCacheConstant.PERMISSION_CACHE_TYPE);
        if (!cached.isEmpty()) {
            return cached;
        }
        log.warn("Permission cache missed, uid: {}", uid);
        return List.of();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        String uid = String.valueOf(loginId);
        String cacheKey = AuthCacheConstant.ROLE_CACHE_PREFIX + uid;
        List<String> cached = authCacheService.getList(cacheKey, uid, AuthCacheConstant.ROLE_CACHE_TYPE);
        if (!cached.isEmpty()) {
            return cached;
        }
        log.warn("Role cache missed, uid: {}", uid);
        throw new IllegalStateException("User roles not found in gateway cache, uid: " + uid);
    }
}