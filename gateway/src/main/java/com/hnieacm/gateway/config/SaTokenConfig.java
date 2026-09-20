package com.hnieacm.gateway.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaHttpMethod;
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
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/13
 * @Description: Sa-Token 安全认证配置
 */
@Slf4j
@Configuration
public class SaTokenConfig {

    /**
     * 注册页面所需的基础数据选项：仅精确放行以下 GET 路径，游客可访问。
     * 不放开任意学院子路径或写方法，教师/助教查询（/api/classes/{id}/teachers、/tas）仍需登录。
     */
    private static final String[] REGISTRATION_BASE_DATA_GET_PATHS = {
            "/api/colleges",
            "/api/colleges/*/grades",
            "/api/colleges/*/grades/*/classes"
    };

    /**
     * 题面图片读取：仅精确放行 GET /oj/images/{id}/{filename}（两段路径），游客可访问。
     * 其余写方法、更深子路径（如 testdata）仍需登录，避免暴露题目答案。
     */
    private static final String[] PROBLEM_IMAGE_GET_PATHS = {"/oj/images/*/*"};

    private static final SaHttpMethod[] GET_METHOD = {SaHttpMethod.GET};
    private static final SaHttpMethod[] POST_METHOD = {SaHttpMethod.POST};
    private static final SaHttpMethod[] PUT_METHOD = {SaHttpMethod.PUT};
    private static final SaHttpMethod[] DELETE_METHOD = {SaHttpMethod.DELETE};

    @Bean
    public SaReactorFilter saReactorFilter(ObjectMapper objectMapper) {
        return new SaReactorFilter()
                .addInclude("/**")
                .addExclude("/favicon.ico")
                .addExclude("/actuator/**")
                .addExclude("/api/auth/login")
                .addExclude("/api/auth/register")
                .addExclude("/api/system/public-config")
                .addExclude("/api/system/time")
                .addExclude("/api/judge/temp-token")
                .addExclude("/judge/problems/**")
                .addExclude("/judge/submissions/**")
                .addExclude("/judge/nodes/**")
                .addExclude("/judge/tasks/**")
                .addExclude("/ws/submissions/**")
                .setAuth(obj -> {
                    // 统一登录态校验（双重保险：exclude + notMatch，避免误拦截登录/注册）
                    SaRouter.match("/**")
                            .notMatch(
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
                                    "/judge/tasks/**",
                                    "/ws/submissions/**"
                            )
                            // 注册基础数据选项：仅 GET 精确路径放行，其余方法与子路径仍需登录
                            .notMatch(r -> SaRouter.isMatchCurrMethod(GET_METHOD)
                                    && SaRouter.isMatchCurrURI(REGISTRATION_BASE_DATA_GET_PATHS))
                            // 题面图片读取：仅 GET 两段路径放行，testdata 等更深子路径仍需登录
                            .notMatch(r -> SaRouter.isMatchCurrMethod(GET_METHOD)
                                    && SaRouter.isMatchCurrURI(PROBLEM_IMAGE_GET_PATHS))
                            .check(r -> {
                                StpUtil.checkLogin();
                                // 滑动过期：在每个经过身份验证的请求上续订令牌/会话TTL
                                long timeoutSeconds = SaManager.getConfig().getTimeout();
                                if (timeoutSeconds > 0) {
                                    StpUtil.renewTimeout(timeoutSeconds);
                                }
                            });

                    // 角色校验
                    SaRouter.match("/api/admin/**", r -> StpUtil.checkRoleOr(RoleConstant.ADMIN, RoleConstant.ROOT));

                    // 注册审核接口：hnieoj-user 未启用 SaInterceptor，网关按 Controller 已声明的 ADMIN/ROOT 补齐校验
                    SaRouter.match("/api/registrations", "/api/registrations/**")
                            .check(r -> StpUtil.checkRoleOr(RoleConstant.ADMIN, RoleConstant.ROOT));

                    // 题目管理权限校验
                    SaRouter.match("/api/problem/add", r -> StpUtil.checkPermission(PermissionConstant.PROBLEM_CREATE));
                    SaRouter.match("/api/problem/edit/**", r -> StpUtil.checkPermission(PermissionConstant.PROBLEM_UPDATE));
                    SaRouter.match("/api/problem/delete/**", r -> StpUtil.checkPermission(PermissionConstant.PROBLEM_DELETE));

                    // 管理端题目详情读取：hnieoj-problem 未注册 SaInterceptor，Controller 注解不会生效。
                    // 网关按 Controller 声明的 PROBLEM_UPDATE 补齐校验，仅覆盖 GET /api/admin/problem/{id}
                    // （排除已有的 /api/admin/problem/list），不改动其它管理题目路由。
                    SaRouter.match("/api/admin/problem/*", r -> {
                        if (SaRouter.isMatchCurrMethod(GET_METHOD)
                                && !SaRouter.isMatchCurrURI("/api/admin/problem/list")) {
                            StpUtil.checkPermission(PermissionConstant.PROBLEM_UPDATE);
                        }
                    });

                    // 标签管理写入：hnieoj-problem 未注册 SaInterceptor，Controller 注解不会生效。
                    // 网关按 Controller 声明的 PROBLEM_CREATE/UPDATE/DELETE 补齐校验，角色仍由 /api/admin/** 规则保证。
                    SaRouter.match("/api/admin/tags", r -> {
                        if (SaRouter.isMatchCurrMethod(POST_METHOD)) {
                            StpUtil.checkPermission(PermissionConstant.PROBLEM_CREATE);
                        }
                    });
                    SaRouter.match("/api/admin/tags/*", r -> {
                        if (SaRouter.isMatchCurrMethod(PUT_METHOD)) {
                            StpUtil.checkPermission(PermissionConstant.PROBLEM_UPDATE);
                        } else if (SaRouter.isMatchCurrMethod(DELETE_METHOD)) {
                            StpUtil.checkPermission(PermissionConstant.PROBLEM_DELETE);
                        }
                    });

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
                })
                .setError(ex -> {
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

                    // SaReactorFilter 随后通过 SaReactorOperateUtil.writeResult 写出字符串，
                    // 其仅在响应尚未设置 Content-Type 时补默认 text/plain;charset=utf-8。
                    // 这里先显式设置为 application/json，确保前端 download helper 能把 401/403
                    // 业务错误按 JSON 解析，而不是当作可下载文件保存。该回调在 filter()
                    // 清除上下文之前执行，可安全取回当前 exchange。
                    ServerWebExchange exchange = SaReactorSyncHolder.getExchange();
                    if (exchange != null) {
                        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    }

                    try {
                        return objectMapper.writeValueAsString(result);
                    } catch (Exception e) {
                        return Result.error(result.getCode(), result.getMsg());
                    }
                });
    }
}
