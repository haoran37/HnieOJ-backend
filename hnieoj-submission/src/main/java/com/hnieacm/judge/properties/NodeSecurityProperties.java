package com.hnieacm.judge.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * 节点身份协议 v1（Ed25519 + 短期 NODE_ACCESS）安全参数。
 *
 * <p>替代将退休的旧 bearer-only 临时令牌协议中的 allowed-clock-skew / nonce-ttl 语义：
 * {@code allowedClockSkewSeconds} 约束入站消息时间戳，{@code nonceTtlSeconds} 约束认证挑战与
 * HTTP 签名 nonce 的防重放窗口。accessTokenSecret 只允许来自运行时环境/文件。</p>
 *
 * @author Codex
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "hnieoj.judge.node-security")
public class NodeSecurityProperties {

    /** 短期令牌受众，所有副本必须一致。 */
    private String audience = "hnieoj-judge-node";

    /** NODE_ACCESS HMAC 签名密钥，仅运行时注入。 */
    private String accessTokenSecret;

    private long accessTokenTtlSeconds = 900;

    private long challengeTtlSeconds = 30;

    private long authDeadlineSeconds = 10;

    private long allowedClockSkewSeconds = 30;

    private long nonceTtlSeconds = 300;

    private long rotationPendingTtlSeconds = 900;

    private long rotationGraceSeconds = 300;

    private int maxControlFrameBytes = 65536;

    private int maxTaskFrameBytes = 4194304;

    /** 单实例最大并发节点连接数，超过时拒绝新连接（fail-closed）。 */
    private int maxConnections = 512;

    /** 单连接入站消息速率上限（每窗口）。 */
    private int messageRateLimit = 200;

    /** 单连接入站消息速率窗口秒数。 */
    private long messageRateWindowSeconds = 10;

    /** 已认证连接空闲上限秒数，超时关闭以释放资源。 */
    private long idleTimeoutSeconds = 120;

    /** 每连接出站写队列容量，队列满即关闭连接，避免内存无限增长。 */
    private int writerQueueCapacity = 64;
}
