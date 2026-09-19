package com.hnieacm.common.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 判题节点任务级访问请求：problem 服务原样透传 HTTP 签名上下文，submission 统一校验。
 *
 * <p>字段与 {@code NodeProtocolConstants} 的 HTTP 签名规范一致：{@code method}/{@code pathWithQuery}
 * 为原始目标，{@code bodySha256} 为实际接收请求体的 SHA-256，{@code signature} 覆盖全部签名字段。
 * 由 problem 下载 Controller 提取后经现有内部授权 Feign 传给 submission，避免 Controller 内写业务逻辑。
 * 所有字段均有界，配合 {@code @Valid} 在入口进行 Bean Validation。</p>
 *
 * @author Codex
 */
@Data
public class JudgeTaskAccessRequest {

    /** NODE_ACCESS 短期令牌（Bearer 明文）。 */
    @Size(max = 4096, message = "authorization 过长")
    private String authorization;

    @Size(max = 64, message = "nodeId 过长")
    private String nodeId;

    @Size(max = 64, message = "keyId 过长")
    private String keyId;

    @Size(max = 16, message = "method 过长")
    private String method;

    @Size(max = 1024, message = "pathWithQuery 过长")
    private String pathWithQuery;

    /** 请求体 SHA-256 十六进制摘要。 */
    @Size(max = 64, message = "bodySha256 过长")
    @Pattern(regexp = "^[0-9a-fA-F]{64}$", message = "bodySha256 必须为 64 位十六进制")
    private String bodySha256;

    /** 请求时间戳，Unix 毫秒。 */
    @Size(max = 20, message = "timestamp 过长")
    private String timestamp;

    @Size(max = 128, message = "nonce 过长")
    private String nonce;

    @Size(max = 1024, message = "signature 过长")
    private String signature;

    /** 任务绑定：下载必须与已领取的租约、题目一致。 */
    @Size(max = 64, message = "submissionId 过长")
    private String submissionId;

    @Size(max = 64, message = "judgeTaskId 过长")
    private String judgeTaskId;

    @Size(max = 64, message = "attemptId 过长")
    private String attemptId;

    @Min(value = 1, message = "problemId 必须大于 0")
    private Long problemId;
}
