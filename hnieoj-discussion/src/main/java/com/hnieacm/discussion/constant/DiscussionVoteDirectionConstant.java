package com.hnieacm.discussion.constant;

import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 点赞方向常量
 */
public final class DiscussionVoteDirectionConstant {

    public static final String UP = "up";
    public static final String DOWN = "down";

    private DiscussionVoteDirectionConstant() {
    }

    public static String normalize(String direction) {
        if (direction == null || direction.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "direction 不能为空，仅支持 up/down");
        }
        String normalized = direction.trim();
        if (UP.equalsIgnoreCase(normalized)) {
            return UP;
        }
        if (DOWN.equalsIgnoreCase(normalized)) {
            return DOWN;
        }
        throw new BizException(ResultCode.BAD_REQUEST, "direction 参数不合法，仅支持 up/down");
    }
}
