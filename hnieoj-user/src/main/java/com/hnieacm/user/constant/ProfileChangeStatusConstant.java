package com.hnieacm.user.constant;

import java.util.Locale;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 用户身份资料变更申请状态常量
 */
public class ProfileChangeStatusConstant {

    private ProfileChangeStatusConstant() {
    }

    public static final String PENDING = "PENDING";

    public static final String APPROVED = "APPROVED";

    public static final String REJECTED = "REJECTED";

    /**
     * 规范化并校验状态，空值返回 null（表示不过滤），非法值返回 null。
     */
    public static String normalize(String status) {
        if (status == null) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        if (!PENDING.equals(normalized) && !APPROVED.equals(normalized) && !REJECTED.equals(normalized)) {
            return null;
        }
        return normalized;
    }

    public static boolean isValid(String status) {
        return PENDING.equals(status) || APPROVED.equals(status) || REJECTED.equals(status);
    }
}
