package com.hnieacm.training.constant;

import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 题单访问权限常量
 */
public final class TrainingAuthConstant {

    public static final String PUBLIC = "Public";
    public static final String PRIVATE = "Private";

    private TrainingAuthConstant() {
    }

    public static String normalize(String auth) {
        if (auth == null || auth.isBlank()) {
            return null;
        }
        String normalized = auth.trim();
        if (PUBLIC.equalsIgnoreCase(normalized)) {
            return PUBLIC;
        }
        if (PRIVATE.equalsIgnoreCase(normalized)) {
            return PRIVATE;
        }
        throw new BizException(ResultCode.BAD_REQUEST, "auth 参数不合法，仅支持 Public/Private");
    }
}
