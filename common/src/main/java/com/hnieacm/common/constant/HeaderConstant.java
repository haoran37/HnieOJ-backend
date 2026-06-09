package com.hnieacm.common.constant;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/13
 * @Description: HTTP Header 常量。
 */
public class HeaderConstant {

    private HeaderConstant() {
    }

    public static final String AUTHORIZATION = "Authorization";
    public static final String INTERNAL_TOKEN = "X-Internal-Token";
    public static final String JUDGE_TOKEN = "X-Judge-Token";
    public static final String JUDGE_NODE_ID = "X-Judge-Node-Id";
    public static final String JUDGE_TOKEN_ID = "X-Judge-Token-Id";
    public static final String JUDGE_INSTANCE_ID = "X-Judge-Instance-Id";
    public static final String JUDGE_FINGERPRINT = "X-Judge-Fingerprint";
    public static final String JUDGE_SIGNATURE_ALGORITHM = "X-Judge-Signature-Algorithm";
    public static final String JUDGE_TIMESTAMP = "X-Judge-Timestamp";
    public static final String JUDGE_NONCE = "X-Judge-Nonce";
    public static final String JUDGE_BODY_SHA256 = "X-Judge-Body-Sha256";
    public static final String JUDGE_SIGNATURE = "X-Judge-Signature";
}
