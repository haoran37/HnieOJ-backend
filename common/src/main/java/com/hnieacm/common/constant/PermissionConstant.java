package com.hnieacm.common.constant;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/13
 * @Description: 权限标识常量（Sa-Token permission key）。
 */
public class PermissionConstant {

    private PermissionConstant() {
    }

    public static final String ALL = "*:*";

    public static final String USER_MANAGE = "user:manage";

    public static final String PROBLEM_CREATE = "problem:create";
    public static final String PROBLEM_UPDATE = "problem:update";
    public static final String PROBLEM_DELETE = "problem:delete";

    public static final String CONTEST_MANAGE = "contest:manage";
    public static final String CONTEST_CREATE = "contest:create";
}
