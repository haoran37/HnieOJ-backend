package com.hnieacm.judge.dto;

import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 判题节点 Token 内部校验请求
 */
@Data
public class ValidateJudgeNodeTokenRequest {

    private String judgeToken;

    private String bearerToken;

    private String method;

    private String pathWithQuery;

    private String sourceIp;

    private String nodeIdHeader;

    private String tokenIdHeader;

    private String instanceId;

    private String fingerprintHash;

    private String signatureAlgorithm;

    private String timestamp;

    private String nonce;

    private String bodySha256;

    private String actualBodySha256;

    private String signature;
}
