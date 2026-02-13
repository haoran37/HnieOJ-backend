package com.hnieacm.auth.controller;

import com.hnieacm.auth.service.AuthPermissionService;
import com.hnieacm.common.result.Result;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/13
 * @Description: 内部权限查询接口
 */
@Hidden
@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class InternalAuthController {

    private final AuthPermissionService authPermissionService;

    /**
     * @MethodName getUserPermissions
     * @Param uid
     * @Description 获取用户权限
     * @Return @return {@link Result }<{@link List }<{@link String }>>
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    @GetMapping("/permissions/{uid}")
    public Result<List<String>> getUserPermissions(@PathVariable String uid) {
        return Result.success(authPermissionService.getUserPermissions(uid));
    }

    /**
     * @MethodName getUserRoles
     * @Param uid
     * @Description 获取用户角色
     * @Return @return {@link Result }<{@link List }<{@link String }>>
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    @GetMapping("/roles/{uid}")
    public Result<List<String>> getUserRoles(@PathVariable String uid) {
        return Result.success(authPermissionService.getUserRoles(uid));
    }
}

