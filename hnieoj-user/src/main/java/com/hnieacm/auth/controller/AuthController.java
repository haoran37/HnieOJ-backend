package com.hnieacm.auth.controller;

import com.hnieacm.auth.dto.LoginRequest;
import com.hnieacm.auth.dto.LoginVo;
import com.hnieacm.auth.dto.RegisterRequest;
import com.hnieacm.auth.service.AuthService;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.common.constant.RegisterModeConstant;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.user.feign.SubmissionInternalFeignClient;
import com.hnieacm.user.vo.RegisterEmailCheckVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
    private final SubmissionInternalFeignClient submissionInternalFeignClient;

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
    public Result<LoginVo> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        log.info("User login, uid: {}", request.getUid());
        LoginVo loginVo = authService.login(request, resolveClientIp(servletRequest));
        return Result.success("登录成功", loginVo);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
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
        Result<RegisterEmailCheckVo> policy = submissionInternalFeignClient.checkRegisterEmail(request.getEmail());
        if (policy == null || policy.getCode() != ResultCode.SUCCESS || policy.getData() == null) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "注册策略暂不可用");
        }
        if (!Boolean.TRUE.equals(policy.getData().getAllowRegister())) {
            throw new BizException(ResultCode.FORBIDDEN, policy.getData().getReason());
        }
        if (RegisterModeConstant.INVITE_CODE.equals(policy.getData().getRegisterMode())) {
            authService.registerWithInvite(request);
            return Result.success("注册申请提交成功，请等待审核", null);
        }
        if (!Boolean.TRUE.equals(policy.getData().getMatched())) {
            throw new BizException(ResultCode.BAD_REQUEST, policy.getData().getReason());
        }
        authService.register(request);
        return Result.success("注册申请提交成功，请等待审核", null);
    }
}
