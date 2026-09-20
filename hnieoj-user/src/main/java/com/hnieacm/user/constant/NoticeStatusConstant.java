package com.hnieacm.user.constant;

import java.util.Locale;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 定向通知状态常量（草稿/已发布）
 */
public class NoticeStatusConstant {

    private NoticeStatusConstant() {
    }

    /**
     * 草稿：可编辑正文与目标，尚未向任何收件人投递。
     */
    public static final String DRAFT = "DRAFT";

    /**
     * 已发布：收件人快照已写入 user_message，正文与目标不可再编辑。
     */
    public static final String PUBLISHED = "PUBLISHED";

    /**
     * 规范化并校验通知状态，空值返回 null（表示不过滤），非法值返回 null。
     */
    public static String normalize(String status) {
        if (status == null) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        if (!DRAFT.equals(normalized) && !PUBLISHED.equals(normalized)) {
            return null;
        }
        return normalized;
    }

    public static boolean isValid(String status) {
        return DRAFT.equals(status) || PUBLISHED.equals(status);
    }
}
