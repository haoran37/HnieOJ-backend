package com.hnieacm.auth.controller;

import com.hnieacm.auth.service.UserAuthCacheService;
import com.hnieacm.common.result.Result;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/14
 * @Description: 内部缓存维护接口（供内部服务在用户角色/权限变更后刷新网关鉴权缓存）
 */
@Hidden
@RestController
@RequestMapping("/internal/auth/cache")
@RequiredArgsConstructor
public class InternalAuthCacheController {

    private final UserAuthCacheService userAuthCacheService;

    @PostMapping("/refresh/{uid}")
    public Result<Void> refreshUserAuthCache(@PathVariable String uid) {
        userAuthCacheService.cacheUserAuth(uid);
        return Result.success(null);
    }
}

