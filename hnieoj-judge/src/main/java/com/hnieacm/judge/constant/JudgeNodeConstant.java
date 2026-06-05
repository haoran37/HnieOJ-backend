package com.hnieacm.judge.constant;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 判题节点安全常量
 */
public class JudgeNodeConstant {

    private JudgeNodeConstant() {
    }

    public static final String NODE_TYPE_FORMAL = "formal";
    public static final String NODE_TYPE_TEMP = "temp";

    public static final String AUTH_CODE_ENABLED = "enabled";
    public static final String AUTH_CODE_REVOKED = "revoked";
    public static final String AUTH_CODE_EXPIRED = "expired";

    public static final String TOKEN_ACTIVE = "active";
    public static final String TOKEN_REVOKED = "revoked";
    public static final String TOKEN_EXPIRED = "expired";
}
