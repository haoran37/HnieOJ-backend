package com.hnieacm.contest.constant;

import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 比赛权限常量
 */
public final class ContestAuthConstant {

    public static final int PUBLIC = 0;
    public static final int PRIVATE = 1;

    private static final String NAME_PUBLIC = "Public";
    private static final String NAME_PRIVATE = "Private";

    private ContestAuthConstant() {
    }

    public static String toName(Integer auth) {
        if (auth == null || auth == PUBLIC) {
            return NAME_PUBLIC;
        }
        if (auth == PRIVATE) {
            return NAME_PRIVATE;
        }
        return NAME_PUBLIC;
    }

    public static Integer fromName(String authName) {
        if (authName == null || authName.isBlank()) {
            return null;
        }
        String normalized = authName.trim();
        if (NAME_PUBLIC.equalsIgnoreCase(normalized)) {
            return PUBLIC;
        }
        if (NAME_PRIVATE.equalsIgnoreCase(normalized)) {
            return PRIVATE;
        }
        throw new BizException(ResultCode.BAD_REQUEST, "auth 参数不合法，仅支持 Public/Private");
    }
}
