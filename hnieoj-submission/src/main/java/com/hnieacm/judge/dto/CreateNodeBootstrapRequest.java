package com.hnieacm.judge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 管理员创建节点 Bootstrap 一次性凭据。
 *
 * @author Codex
 */
@Data
public class CreateNodeBootstrapRequest {

    @NotBlank(message = "nodeType 不能为空")
    @Pattern(regexp = "formal|temp", message = "nodeType 必须为 formal 或 temp")
    private String nodeType;

    @Size(max = 100, message = "nodeName 长度不能超过 100")
    private String nodeName;

    @NotNull(message = "maxConcurrency 不能为空")
    @Min(value = 1, message = "maxConcurrency 必须大于 0")
    @Max(value = 1000, message = "maxConcurrency 过大")
    private Integer maxConcurrency;

    private List<String> supportedJudgeModes;

    @Min(value = 1, message = "weight 必须大于 0")
    @Max(value = 100, message = "weight 过大")
    private Integer weight;

    /** Bootstrap 过期毫秒时间戳。 */
    @NotNull(message = "expiresAt 不能为空")
    private Long expiresAt;

    /** 节点硬授权截止毫秒时间戳；temp 必填且必须大于当前时间。 */
    private Long authorizationUntil;

    @Size(max = 255, message = "remark 长度不能超过 255")
    private String remark;
}
