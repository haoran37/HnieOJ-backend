package com.hnieacm.announcement.constant;

import java.util.Set;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 公告/新闻分类常量
 */
public final class AnnouncementCategoryConstant {

    private AnnouncementCategoryConstant() {
    }

    /**
     * 普通公告
     */
    public static final String ANNOUNCEMENT = "ANNOUNCEMENT";

    /**
     * 新闻
     */
    public static final String NEWS = "NEWS";

    private static final Set<String> ALL = Set.of(ANNOUNCEMENT, NEWS);

    public static boolean isValid(String category) {
        return category != null && ALL.contains(category);
    }
}
