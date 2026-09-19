package com.hnieacm.judge.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 注册成功后的节点身份（只包含非机密信息）。
 *
 * @author Codex
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeEnrollVo {

    private String nodeId;

    private String keyId;

    private String nodeType;

    private Long authorizationUntil;

    private Integer maxConcurrency;

    private List<String> supportedJudgeModes;
}
