package com.hnieacm.contest.constant;

import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 比赛赛制常量
 */
public final class ContestTypeConstant {

    public static final int ACM = 0;
    public static final int OI = 1;

    private static final String NAME_ACM = "ACM";
    private static final String NAME_OI = "OI";

    private ContestTypeConstant() {
    }

    public static String toName(Integer type) {
        if (type == null || type == ACM) {
            return NAME_ACM;
        }
        if (type == OI) {
            return NAME_OI;
        }
        return NAME_ACM;
    }

    public static Integer fromName(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return null;
        }
        String normalized = typeName.trim();
        if (NAME_ACM.equalsIgnoreCase(normalized)) {
            return ACM;
        }
        if (NAME_OI.equalsIgnoreCase(normalized) || "IOI".equalsIgnoreCase(normalized)) {
            return OI;
        }
        throw new BizException(ResultCode.BAD_REQUEST, "type 参数不合法，仅支持 ACM/OI");
    }
}
