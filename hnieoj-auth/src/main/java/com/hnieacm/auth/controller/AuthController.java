package com.hnieacm.auth.controller;

import com.hnieacm.auth.dto.LoginRequest;
import com.hnieacm.auth.dto.LoginVo;
import com.hnieacm.auth.dto.RegisterRequest;
import com.hnieacm.auth.service.AuthService;
import com.hnieacm.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/13
 * @Description: 认证控制器。
 */
@Slf4j
@Tag(name = "认证模块")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * @MethodName login
     * @Param request
     * @Description 登录
     * @Return @return {@link Result }<{@link LoginVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginVo> login(@Valid @RequestBody LoginRequest request) {
        log.info("User login, uid: {}", request.getUid());
        LoginVo loginVo = authService.login(request);
        return Result.success("登录成功", loginVo);
    }

    /**
     * @MethodName register
     * @Param request
     * @Description 注册
     * @Return @return {@link Result }<{@link Void }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        log.info("User register apply submit, uid: {}", request.getUid());
        authService.register(request);
        return Result.success("注册申请提交成功，请等待审核", null);
    }
}
