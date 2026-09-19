package com.hnieacm.judge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

/**
 * 管理员更新节点运行策略（模式 / 额度 / 权重 / 授权截止）。
 *
 * <p>字段为可选项：仅更新显式提供的字段。权限相关的模式/额度/授权截止变更
 * 会提升 accessVersion 使旧短期授权失效；纯权重变更不影响在途写。</p>
 *
 * @author Codex
 */
@Data
public class UpdateNodePolicyRequest {

    private List<String> supportedJudgeModes;

    @Min(value = 1, message = "maxConcurrency 必须大于 0")
    @Max(value = 1000, message = "maxConcurrency 过大")
    private Integer maxConcurrency;

    @Min(value = 1, message = "weight 必须大于 0")
    @Max(value = 100, message = "weight 过大")
    private Integer weight;

    /** 新的节点硬授权截止毫秒时间戳；必须大于当前时间。 */
    private Long authorizationUntil;
}
