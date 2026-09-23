package com.hnieacm.gateway.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.exception.SaTokenException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/13
 * @Description: 网关全局异常处理器
 */
@Slf4j
@Order(-1)
@Component
@RequiredArgsConstructor
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    /**
     * @MethodName handle
     * @Param exchange
     * @Param ex
     * @Description 处理异常并返回标准化响应
     * @Return @return {@link Mono }<{@link Void }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    @NonNull
@Override
public Mono<Void> handle(ServerWebExchange exchange, @NonNull Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        // 响应已提交则无法再修改，直接返回错误
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        // 根据异常类型构建响应结果
        Result<?> result = buildErrorResult(ex);
        HttpStatus httpStatus = determineHttpStatus(ex);

        // 设置响应状态和内容类型
        response.setStatusCode(httpStatus);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // 序列化并写入响应体
        return response.writeWith(Mono.fromSupplier(() -> {
            DataBufferFactory bufferFactory = response.bufferFactory();
            try {
                byte[] bytes = objectMapper.writeValueAsBytes(result);
                return bufferFactory.wrap(bytes);
            } catch (Exception e) {
                log.error("序列化异常响应失败", e);
                return bufferFactory.wrap(new byte[0]);
            }
        }));
    }

    /**
     * @MethodName buildErrorResult
     * @Param ex
     * @Description 根据异常类型构建错误结果
     * @Return @return {@link Result }<{@link ? }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    private Result<?> buildErrorResult(Throwable ex) {
        // Sa-Token 认证异常
        if (ex instanceof NotLoginException) {
            return Result.error(ResultCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        
        // Sa-Token 角色权限异常
        if (ex instanceof NotRoleException nre) {
            return Result.error(ResultCode.FORBIDDEN, "缺少角色权限: " + nre.getRole());
        }
        
        // Sa-Token 操作权限异常
        if (ex instanceof NotPermissionException npe) {
            return Result.error(ResultCode.FORBIDDEN, "缺少操作权限: " + npe.getPermission());
        }
        
        // Sa-Token 其他异常
        if (ex instanceof SaTokenException) {
            return Result.error(ResultCode.UNAUTHORIZED, ex.getMessage());
        }
        
        // Spring WebFlux 响应状态异常
        if (ex instanceof ResponseStatusException rse) {
            int httpStatus = rse.getStatusCode().value();
            String reason = rse.getReason();

            if (httpStatus >= 500) {
                return Result.error(ResultCode.INTERNAL_ERROR, "系统异常，请稍后重试");
            }

            String msg = (reason == null || reason.isBlank()) ? "请求失败" : reason;
            return Result.error(httpStatus, msg);
        }
        
        // 未知异常：记录详细日志，返回脱敏信息
        log.error("网关捕获未处理异常: {}", ex.getClass().getName(), ex);
        return Result.error(ResultCode.INTERNAL_ERROR, "系统异常，请稍后重试");
    }

    /**
     * @MethodName determineHttpStatus
     * @Param ex
     * @Description 根据异常类型确定 HTTP 状态码
     * @Return @return {@link HttpStatus }
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    private HttpStatus determineHttpStatus(Throwable ex) {
        if (ex instanceof NotLoginException) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (ex instanceof NotRoleException || ex instanceof NotPermissionException) {
            return HttpStatus.FORBIDDEN;
        }
        if (ex instanceof ResponseStatusException) {
            return HttpStatus.valueOf(((ResponseStatusException) ex).getStatusCode().value());
        }
        // 业务异常统一返回 200，由业务码区分
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
