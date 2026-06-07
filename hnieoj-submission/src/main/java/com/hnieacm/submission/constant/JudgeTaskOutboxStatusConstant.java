package com.hnieacm.submission.constant;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 判题任务 outbox 状态常量
 */
public class JudgeTaskOutboxStatusConstant {

    private JudgeTaskOutboxStatusConstant() {
    }

    public static final String PENDING = "pending";

    public static final String PROCESSING = "processing";

    public static final String SENT = "sent";

    public static final String FAILED = "failed";

    public static final String EXHAUSTED = "exhausted";
}
