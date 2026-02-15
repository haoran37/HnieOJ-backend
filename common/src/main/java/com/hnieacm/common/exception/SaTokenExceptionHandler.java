package com.hnieacm.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.exception.SaTokenException;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: Sa-Token 相关异常统一转换为标准 Result 返回
 */
@Slf4j
@RestControllerAdvice
public class SaTokenExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public Result<?> handleNotLogin(NotLoginException e) {
        log.warn("Not login: {}", e.getMessage());
        return Result.error(ResultCode.UNAUTHORIZED, "未登录或登录已过期");
    }

    @ExceptionHandler(NotRoleException.class)
    public Result<?> handleNotRole(NotRoleException e) {
        log.warn("Not role: {}", e.getMessage());
        return Result.error(ResultCode.FORBIDDEN, "缺少角色权限");
    }

    @ExceptionHandler(NotPermissionException.class)
    public Result<?> handleNotPermission(NotPermissionException e) {
        log.warn("Not permission: {}", e.getMessage());
        return Result.error(ResultCode.FORBIDDEN, "缺少操作权限");
    }

    @ExceptionHandler(SaTokenException.class)
    public Result<?> handleSaTokenException(SaTokenException e) {
        log.warn("Sa-Token error: {}", e.getMessage());
        return Result.error(ResultCode.UNAUTHORIZED, "认证失败");
    }
}

