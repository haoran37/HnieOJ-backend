package com.hnieacm.training.constant;

import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 题单状态常量
 */
public final class TrainingStatusConstant {

    public static final int DISABLED = 0;
    public static final int ENABLED = 1;

    private TrainingStatusConstant() {
    }

    public static int toDbStatus(Boolean status) {
        if (status == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "status 不能为空");
        }
        return Boolean.TRUE.equals(status) ? ENABLED : DISABLED;
    }

    public static boolean isEnabled(Integer status) {
        return status != null && status == ENABLED;
    }
}
