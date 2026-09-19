package com.hnieacm.judge.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bootstrap 一次性凭据签发结果（明文只返回一次）。
 *
 * @author Codex
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeBootstrapVo {

    private Long authCodeId;

    private String bootstrapToken;

    private String nodeType;

    private long expiresAt;
}
