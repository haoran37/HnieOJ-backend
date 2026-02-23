package com.hnieacm.discussion.constant;

import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 讨论排序常量
 */
public final class DiscussionSortConstant {

    public static final String LATEST = "Latest";
    public static final String HOT = "Hot";

    private DiscussionSortConstant() {
    }

    public static String normalize(String sort) {
        if (sort == null || sort.isBlank()) {
            return LATEST;
        }
        String normalized = sort.trim();
        if (LATEST.equalsIgnoreCase(normalized)) {
            return LATEST;
        }
        if (HOT.equalsIgnoreCase(normalized)) {
            return HOT;
        }
        throw new BizException(ResultCode.BAD_REQUEST, "sort 参数不合法，仅支持 Latest/Hot");
    }
}
