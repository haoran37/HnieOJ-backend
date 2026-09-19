package com.hnieacm.submission.constant;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/18
 * @Description: 判题任务执行租约状态常量
 */
public class JudgeTaskExecutionStatusConstant {

    private JudgeTaskExecutionStatusConstant() {
    }

    /**
     * 已入库待领取。
     */
    public static final String QUEUED = "queued";

    /**
     * 已被节点领取，租约有效。
     */
    public static final String LEASED = "leased";

    /**
     * 节点已上报运行中进度，租约有效。
     */
    public static final String RUNNING = "running";

    /**
     * 已产出终态结果。
     */
    public static final String COMPLETED = "completed";

    /**
     * 重派预算耗尽，最终 SYSTEM_ERROR。
     */
    public static final String FAILED = "failed";
}
