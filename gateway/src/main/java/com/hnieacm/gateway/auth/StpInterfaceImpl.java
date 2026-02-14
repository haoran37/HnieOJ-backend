package com.hnieacm.gateway.auth;

import cn.dev33.satoken.stp.StpInterface;
import com.hnieacm.common.constant.AuthCacheConstant;
import com.hnieacm.gateway.service.AuthRemoteQueryService;
import com.hnieacm.gateway.service.AuthCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/13
 * @Description: Sa-Token 权限认证接口实现
 * @Attention: 该接口为同步接口，且运行在 Reactor 线程上，禁止使用 Mono.block() 等 Reactor 阻塞 API,
 *             当前实现只从 Redis 缓存读取角色/权限数据，缓存由认证服务在登录时写入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final AuthCacheService authCacheService;
    private final AuthRemoteQueryService authRemoteQueryService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        String uid = String.valueOf(loginId);
        String cacheKey = AuthCacheConstant.PERMISSION_CACHE_PREFIX + uid;
        List<String> cached = authCacheService.getList(cacheKey, uid, AuthCacheConstant.PERMISSION_CACHE_TYPE);
        if (!cached.isEmpty()) {
            return cached;
        }

        List<String> loaded = authRemoteQueryService.queryPermissions(uid);
        authCacheService.setList(cacheKey, loaded, uid, AuthCacheConstant.PERMISSION_CACHE_TYPE);
        // permissions 允许为空（学生无额外权限）
        return loaded;
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        String uid = String.valueOf(loginId);
        String cacheKey = AuthCacheConstant.ROLE_CACHE_PREFIX + uid;
        List<String> cached = authCacheService.getList(cacheKey, uid, AuthCacheConstant.ROLE_CACHE_TYPE);
        if (!cached.isEmpty()) {
            return cached;
        }

        List<String> loaded = authRemoteQueryService.queryRoles(uid);
        authCacheService.setList(cacheKey, loaded, uid, AuthCacheConstant.ROLE_CACHE_TYPE);

        // role 不应为空（最少应为 student），为空视为权限数据异常
        if (loaded == null || loaded.isEmpty()) {
            throw new IllegalStateException("User roles not found, uid: " + uid);
        }
        return loaded;
    }
}
