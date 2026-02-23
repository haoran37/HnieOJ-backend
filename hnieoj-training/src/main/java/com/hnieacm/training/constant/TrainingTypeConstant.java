package com.hnieacm.training.constant;

import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 题单类型常量
 */
public final class TrainingTypeConstant {

    public static final String OFFICIAL = "Official";
    public static final String USER = "User";

    private TrainingTypeConstant() {
    }

    public static String normalize(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String normalized = type.trim();
        if (OFFICIAL.equalsIgnoreCase(normalized)) {
            return OFFICIAL;
        }
        if (USER.equalsIgnoreCase(normalized)) {
            return USER;
        }
        throw new BizException(ResultCode.BAD_REQUEST, "type 参数不合法，仅支持 Official/User");
    }
}
