package com.hnieacm.common.constant;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/13
 * @Description: RoleId 常量
 * @Attention 若数据库角色 ID 设计调整，需同步更新该常量与相关权限映射逻辑
 */
public class RoleIdConstant {

    private RoleIdConstant() {
    }

    public static final long ROOT = 1000L;
    public static final long ADMIN = 1001L;
    public static final long TEACHER = 1002L;
    public static final long TA = 1003L;
    public static final long STUDENT = 1004L;
}
