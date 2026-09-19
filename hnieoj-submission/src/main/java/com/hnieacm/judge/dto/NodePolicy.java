package com.hnieacm.judge.dto;

import lombok.Data;

import java.util.List;

/**
 * Bootstrap 策略（序列化进 judge_node_auth_code.policy_json）。
 *
 * @author Codex
 */
@Data
public class NodePolicy {

    private Integer maxConcurrency;

    private List<String> supportedJudgeModes;

    private Integer weight;

    private Long authorizationUntil;
}
