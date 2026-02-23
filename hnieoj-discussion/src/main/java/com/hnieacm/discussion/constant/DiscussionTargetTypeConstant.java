package com.hnieacm.discussion.constant;

import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 点赞目标常量
 */
public final class DiscussionTargetTypeConstant {

    public static final String POST = "post";
    public static final String ANSWER = "answer";

    private DiscussionTargetTypeConstant() {
    }

    public static String normalize(String targetType) {
        if (targetType == null || targetType.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "type 不能为空，仅支持 post/answer");
        }
        String normalized = targetType.trim();
        if (POST.equalsIgnoreCase(normalized)) {
            return POST;
        }
        if (ANSWER.equalsIgnoreCase(normalized)) {
            return ANSWER;
        }
        throw new BizException(ResultCode.BAD_REQUEST, "type 参数不合法，仅支持 post/answer");
    }
}
