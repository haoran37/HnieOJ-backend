package com.hnieacm.common.exception;

import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/10
 * @Description: 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * @MethodName handleBizException
     * @Param e 业务异常对象
     * @Description 处理业务异常
     * @Return @return {@link Result }<{@link ? }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/10
     */
    @ExceptionHandler(BizException.class)
    public Result<?> handleBizException(BizException e) {
        log.warn("业务异常: {}", e.getMsg());
        return Result.error(e.getCode(), e.getMsg());
    }

    /**
     * @MethodName handleException
     * @Param e 业务异常对象
     * @Description 处理系统异常
     * @Return @return {@link Result }<{@link ? }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/10
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(ResultCode.INTERNAL_ERROR, "系统异常，请稍后重试");
    }
}
