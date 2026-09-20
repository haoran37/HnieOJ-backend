package com.hnieacm.user.constant;

import java.util.Locale;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 定向通知目标类型常量
 */
public class NoticeTargetTypeConstant {

    private NoticeTargetTypeConstant() {
    }

    /**
     * 目标为用户 uid 集合。
     */
    public static final String USERS = "USERS";

    /**
     * 目标为班级 id 集合，发布时按班级现有学生展开为收件人快照。
     */
    public static final String CLASSES = "CLASSES";

    /**
     * 规范化并校验目标类型，空值/未知类型返回 null。
     */
    public static String normalize(String targetType) {
        if (targetType == null) {
            return null;
        }
        String normalized = targetType.trim().toUpperCase(Locale.ROOT);
        if (!USERS.equals(normalized) && !CLASSES.equals(normalized)) {
            return null;
        }
        return normalized;
    }
}
