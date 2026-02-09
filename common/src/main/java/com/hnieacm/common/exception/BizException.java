package com.hnieacm.common.exception;

import lombok.Getter;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/10
 * @Description: 业务异常
 */
@Getter
public class BizException extends RuntimeException {
    private final int code;
    private final String msg;

    /**
     * @MethodName BizException
     * @Param code
     * @Param msg
     * @Description 根据错误码和错误信息创建异常
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/10
     */
    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
        this.msg = msg;
    }

    /**
     * @MethodName BizException
     * @Param msg
     * @Description 默认错误码为500
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/10
     */
    public BizException(String msg) {
        super(msg);
        this.code = 500;
        this.msg = msg;
    }
}
