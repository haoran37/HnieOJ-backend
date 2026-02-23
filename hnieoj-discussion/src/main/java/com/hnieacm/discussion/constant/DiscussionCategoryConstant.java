package com.hnieacm.discussion.constant;

import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 讨论分类常量
 */
public final class DiscussionCategoryConstant {

    public static final String SITE = "Site";
    public static final String PROBLEM = "Problem";
    public static final String ALL = "All";

    private DiscussionCategoryConstant() {
    }

    public static String normalize(String category) {
        if (category == null || category.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "category 不能为空，仅支持 Site/Problem");
        }
        String normalized = category.trim();
        if (SITE.equalsIgnoreCase(normalized)) {
            return SITE;
        }
        if (PROBLEM.equalsIgnoreCase(normalized)) {
            return PROBLEM;
        }
        throw new BizException(ResultCode.BAD_REQUEST, "category 参数不合法，仅支持 Site/Problem");
    }

    public static String normalizeWithAll(String category) {
        if (category == null || category.isBlank()) {
            return ALL;
        }
        String normalized = category.trim();
        if (ALL.equalsIgnoreCase(normalized)) {
            return ALL;
        }
        return normalize(normalized);
    }
}
