package com.hnieacm.problem.constant;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/20
 * @Description: 题目权限/访问级别常量
 */
public class ProblemAuthConstant {

    private ProblemAuthConstant() {
    }

    /**
     * 公开题目
     */
    public static final int PUBLIC = 1;

    /**
     * 私有题目（仅管理员/教师可以管理/查看）
     */
    public static final int PRIVATE = 2;

    /**
     * 仅限竞赛
     */
    public static final int CONTEST_ONLY = 3;
}

