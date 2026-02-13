package com.hnieacm.common.exception;

import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/10
 * @Description: 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * @MethodName handleValidationException
     * @Param e
     * @Description 处理验证异常
     * @Return @return {@link Result }<{@link ? }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<?> handleValidationException(Exception e) {
        FieldError fieldError = null;
        if (e instanceof MethodArgumentNotValidException manve) {
            fieldError = manve.getBindingResult().getFieldError();
        } else if (e instanceof BindException be) {
            fieldError = be.getBindingResult().getFieldError();
        }

        String msg = "参数校验失败";
        if (fieldError != null && fieldError.getDefaultMessage() != null) {
            msg = fieldError.getDefaultMessage();
        }
        return Result.error(ResultCode.BAD_REQUEST, msg);
    }

    /**
     * @MethodName handleConstraintViolationException
     * @Param e
     * @Description 处理违反约束异常
     * @Return @return {@link Result }<{@link ? }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleConstraintViolationException(ConstraintViolationException e) {
        String msg = e.getConstraintViolations()
                .stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        if (msg.isBlank()) {
            msg = "参数校验失败";
        }
        return Result.error(ResultCode.BAD_REQUEST, msg);
    }

    /**
     * @MethodName handleHttpMessageNotReadableException
     * @Param e
     * @Description 处理http消息不可读异常
     * @Return @return {@link Result }<{@link ? }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<?> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.error(ResultCode.BAD_REQUEST, "请求体格式错误");
    }

    /**
     * @MethodName handleHttpRequestMethodNotSupportedException
     * @Param e
     * @Description 处理不支持http请求方法异常
     * @Return @return {@link Result }<{@link ? }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<?> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        String supportedMethods = null;
        if (e.getSupportedMethods() != null) {
            supportedMethods = String.join(", ", e.getSupportedMethods());
        }
        log.warn("请求方式不支持: {}，支持的请求方式: {}", e.getMessage(), supportedMethods);
        return Result.error(ResultCode.BAD_REQUEST, "请求方式不支持，支持的请求方式: " + supportedMethods);
    }

    /**
     * @MethodName handleNoResourceFoundException
     * @Param e
     * @Description 处理未找到资源异常
     * @Return @return {@link Result }<{@link ? }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<?> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("接口不存在: {}", e.getMessage());
        return Result.error(ResultCode.NOT_FOUND, "接口不存在");
    }

    /**
     * @MethodName handleBizException
     * @Param e
     * @Description 处理业务异常
     * @Return @return {@link Result }<{@link ? }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    @ExceptionHandler(BizException.class)
    public Result<?> handleBizException(BizException e) {
        log.warn("业务异常: {}", e.getMsg());
        return Result.error(e.getCode(), e.getMsg());
    }

    /**
     * @MethodName handleException
     * @Param e
     * @Description 处理异常
     * @Return @return {@link Result }<{@link ? }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(ResultCode.INTERNAL_ERROR, "系统异常，请稍后重试");
    }
}
