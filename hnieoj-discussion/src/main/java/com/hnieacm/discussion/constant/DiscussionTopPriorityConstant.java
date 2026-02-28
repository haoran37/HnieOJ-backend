package com.hnieacm.discussion.constant;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/28
 * @Description: 讨论置顶优先级常量
 */
public final class DiscussionTopPriorityConstant {

    public static final int NORMAL = 0;
    public static final int TOP = 1;

    private DiscussionTopPriorityConstant() {
    }

    public static int fromBoolean(Boolean isTop) {
        return Boolean.TRUE.equals(isTop) ? TOP : NORMAL;
    }
}
