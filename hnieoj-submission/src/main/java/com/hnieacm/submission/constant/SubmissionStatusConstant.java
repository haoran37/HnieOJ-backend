package com.hnieacm.submission.constant;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 提交/判题状态常量
 */
public class SubmissionStatusConstant {

    private SubmissionStatusConstant() {
    }

    /**
     * 待处理状态（尚未判题）
     */
    public static final int PENDING = -10;

    /**
     * 编译中
     */
    public static final int COMPILING = -9;

    /**
     * 运行中
     */
    public static final int RUNNING = -8;

    /**
     * 答案正确
     */
    public static final int ACCEPTED = 0;

    /**
     * 运行错误
     */
    public static final int RUNTIME_ERROR = 1;

    /**
     * 编译错误
     */
    public static final int COMPILE_ERROR = 2;

    /**
     * 答案错误
     */
    public static final int WRONG_ANSWER = 3;

    /**
     * 时间超限
     */
    public static final int TIME_LIMIT_EXCEEDED = 4;

    /**
     * 内存超限
     */
    public static final int MEMORY_LIMIT_EXCEEDED = 5;

    /**
     * 系统错误
     */
    public static final int SYSTEM_ERROR = 6;

    public static String toText(Integer status) {
        if (status == null) {
            return "Unknown";
        }
        return switch (status) {
            case PENDING -> "Pending";
            case COMPILING -> "Compiling";
            case RUNNING -> "Running";
            case ACCEPTED -> "Accepted";
            case RUNTIME_ERROR -> "Runtime Error";
            case COMPILE_ERROR -> "Compile Error";
            case WRONG_ANSWER -> "Wrong Answer";
            case TIME_LIMIT_EXCEEDED -> "Time Limit Exceeded";
            case MEMORY_LIMIT_EXCEEDED -> "Memory Limit Exceeded";
            case SYSTEM_ERROR -> "System Error";
            default -> "Unknown";
        };
    }
}
