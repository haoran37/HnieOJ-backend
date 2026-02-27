package com.hnieacm.user.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.hnieacm.common.result.Result;
import com.hnieacm.user.service.UserLookupService;
import com.hnieacm.user.vo.UserCheckVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/27
 * @Description: 用户查询接口（需要登录）
 */
@Tag(name = "用户查询模块")
@Validated
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@SaCheckLogin
public class UserLookupController {

    private final UserLookupService userLookupService;

    @Operation(summary = "检查用户是否存在")
    @GetMapping("/check")
    public Result<UserCheckVo> checkUser(@RequestParam("query") @NotBlank(message = "query 不能为空") String query) {
        return Result.success(userLookupService.checkUser(query));
    }
}
