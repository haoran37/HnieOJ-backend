package com.hnieacm.common.dto;

import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 判题节点请求签名上下文
 */
@Data
public class JudgeNodeRequestContext {

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
