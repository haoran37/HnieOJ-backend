package com.hnieacm.gateway.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.constant.PermissionConstant;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/13
 * @Description: Sa-Token 安全认证配置
 */
@Slf4j
@Configuration
public class SaTokenConfig {

    /**
     * 无需登录即可访问的公共路径。addExclude 与统一登录态 notMatch 共用同一份定义，避免两处规则漂移。
     */
    static final String[] PUBLIC_PATHS = {
            "/favicon.ico",
            "/actuator/**",
            "/api/auth/login",
            "/api/auth/register",
            "/api/system/public-config",
            "/api/system/time",
            "/api/judge/temp-token",
            "/judge/problems/**",
            "/judge/submissions/**",
            "/judge/nodes/**",
            "/ws/submissions/**",
            // 节点协议 WebSocket：应用层 Ed25519 认证，用户登录态不适用
            "/ws/judge/node"
    };

    @Bean
    public SaReactorFilter saReactorFilter(ObjectMapper objectMapper) {
        return new SaReactorFilter()
                .addInclude("/**")
                .addExclude(PUBLIC_PATHS)
                .setAuth(obj -> registerRouteRules())
                .setError(ex -> buildAuthErrorResponse(objectMapper, ex));
    }

    /**
     * 注册统一登录态、角色与细粒度权限规则。
     */
    private void registerRouteRules() {
        // 统一登录态校验（双重保险：exclude + notMatch，避免误拦截登录/注册）
        SaRouter.match("/**")
                .notMatch(PUBLIC_PATHS)
                .check(r -> {
                    StpUtil.checkLogin();
                    // 滑动过期：在每个经过身份验证的请求上续订令牌/会话TTL
                    long timeoutSeconds = SaManager.getConfig().getTimeout();
                    if (timeoutSeconds > 0) {
                        StpUtil.renewTimeout(timeoutSeconds);
                    }
                });

        registerAdminAndPermissionRules();
    }

    /**
     * 注册管理端角色校验与题目/用户管理权限校验。
     */
    private void registerAdminAndPermissionRules() {
        // 角色校验
        SaRouter.match("/api/admin/**", r -> StpUtil.checkRoleOr(RoleConstant.ADMIN, RoleConstant.ROOT));

        // 题目管理权限校验
        SaRouter.match("/api/problem/add", r -> StpUtil.checkPermission(PermissionConstant.PROBLEM_CREATE));
        SaRouter.match("/api/problem/edit/**", r -> StpUtil.checkPermission(PermissionConstant.PROBLEM_UPDATE));
        SaRouter.match("/api/problem/delete/**", r -> StpUtil.checkPermission(PermissionConstant.PROBLEM_DELETE));

        // 用户管理权限校验；成就接口已合并进 user 服务，保留原有例外规则
        SaRouter.match("/api/users/**")
                // /api/users/{uid}/achievements 为用户成就模块对外接口，不走用户管理权限
                .notMatch("/api/users/*/achievements", "/api/users/*/achievements/**")
                .check(r -> StpUtil.checkPermission(PermissionConstant.USER_MANAGE));
        SaRouter.match("/api/admin/permission/**", r -> StpUtil.checkPermission(PermissionConstant.USER_MANAGE));
        SaRouter.match("/api/admin/users/**")
                // /api/admin/users/{uid}/achievements 为成就管理接口，不走用户管理权限
                .notMatch("/api/admin/users/*/achievements", "/api/admin/users/*/achievements/**")
                .check(r -> StpUtil.checkPermission(PermissionConstant.USER_MANAGE));
    }

    /**
     * 将鉴权异常转换为统一响应；保留原有分支顺序与文案。
     *
     * @param objectMapper JSON 序列化器
     * @param ex           鉴权过程中抛出的异常
     * @return 统一响应体 JSON 字符串；序列化失败时退回 {@link Result} 对象
     */
    Object buildAuthErrorResponse(ObjectMapper objectMapper, Throwable ex) {
        // 便于排查：记录异常类型与 message
        if (ex != null) {
            log.warn("Gateway auth rejected, ex: {}, msg: {}", ex.getClass().getName(), ex.getMessage());
        } else {
            log.warn("Gateway auth rejected, ex is null");
        }

        Result<?> result;
        if (ex instanceof NotLoginException) {
            result = Result.error(ResultCode.UNAUTHORIZED, "未登录或登录已过期");
        } else if (ex instanceof NotRoleException) {
            result = Result.error(ResultCode.FORBIDDEN, "缺少角色权限");
        } else if (ex instanceof NotPermissionException) {
            result = Result.error(ResultCode.FORBIDDEN, "缺少操作权限");
        } else if (ex instanceof SaTokenException && ex.getMessage() != null && !ex.getMessage().isBlank()) {
            result = Result.error(ResultCode.UNAUTHORIZED, ex.getMessage());
        } else if (ex instanceof IllegalStateException) {
            result = Result.error(ResultCode.INTERNAL_ERROR, "权限数据加载失败，请稍后重试");
        } else {
            result = Result.error(ResultCode.UNAUTHORIZED, "认证失败");
        }

        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return Result.error(result.getCode(), result.getMsg());
        }
    }
}
