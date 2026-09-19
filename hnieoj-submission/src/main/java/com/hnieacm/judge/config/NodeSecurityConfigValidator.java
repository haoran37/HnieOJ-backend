package com.hnieacm.judge.config;

import cn.hutool.core.util.StrUtil;
import com.hnieacm.judge.properties.NodeSecurityProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 节点身份协议安全参数启动校验：非法值 fail-fast。
 *
 * <p>范围校验覆盖挑战/令牌/轮换/时钟偏差/帧大小，防止把危险配置带进运行时。
 * 密钥仅校验存在性与长度，具体值不落日志。</p>
 *
 * @author Codex
 */
@Component
@RequiredArgsConstructor
public class NodeSecurityConfigValidator {

    private static final String DEFAULT_SECRET_MARK = "replace_me";
    private static final int MIN_SECRET_LENGTH = 32;
    private static final int MIN_FRAME_BYTES = 1024;
    private static final int MAX_FRAME_BYTES = 64 * 1024 * 1024;

    private final NodeSecurityProperties properties;

    @PostConstruct
    public void validate() {
        String secret = properties.getAccessTokenSecret();
        if (StrUtil.isBlank(secret) || secret.contains(DEFAULT_SECRET_MARK)) {
            throw new IllegalStateException(
                    "HNIEOJ_JUDGE_NODE_ACCESS_TOKEN_SECRET must be configured by runtime environment");
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "HNIEOJ_JUDGE_NODE_ACCESS_TOKEN_SECRET length must be at least 32 characters");
        }
        if (StrUtil.isBlank(properties.getAudience())) {
            throw new IllegalStateException("hnieoj.judge.node-security.audience must not be blank");
        }
        requirePositive("accessTokenTtlSeconds", properties.getAccessTokenTtlSeconds());
        requirePositive("challengeTtlSeconds", properties.getChallengeTtlSeconds());
        requirePositive("authDeadlineSeconds", properties.getAuthDeadlineSeconds());
        requirePositive("nonceTtlSeconds", properties.getNonceTtlSeconds());
        requirePositive("rotationPendingTtlSeconds", properties.getRotationPendingTtlSeconds());
        requirePositive("rotationGraceSeconds", properties.getRotationGraceSeconds());
        requirePositive("idleTimeoutSeconds", properties.getIdleTimeoutSeconds());
        requirePositive("messageRateWindowSeconds", properties.getMessageRateWindowSeconds());
        requirePositive("maxConnections", properties.getMaxConnections());
        requirePositive("messageRateLimit", properties.getMessageRateLimit());
        requirePositive("writerQueueCapacity", properties.getWriterQueueCapacity());
        if (properties.getAllowedClockSkewSeconds() < 0) {
            throw new IllegalStateException("hnieoj.judge.node-security.allowed-clock-skew-seconds must be >= 0");
        }
        requireFrame("maxControlFrameBytes", properties.getMaxControlFrameBytes());
        requireFrame("maxTaskFrameBytes", properties.getMaxTaskFrameBytes());
        if (properties.getMaxTaskFrameBytes() < properties.getMaxControlFrameBytes()) {
            throw new IllegalStateException(
                    "hnieoj.judge.node-security.max-task-frame-bytes must be >= max-control-frame-bytes");
        }
    }

    private void requirePositive(String name, long value) {
        if (value <= 0) {
            throw new IllegalStateException("hnieoj.judge.node-security." + name + " must be > 0");
        }
    }

    private void requireFrame(String name, int value) {
        if (value < MIN_FRAME_BYTES || value > MAX_FRAME_BYTES) {
            throw new IllegalStateException(
                    "hnieoj.judge.node-security." + name + " must be within [" + MIN_FRAME_BYTES + ", "
                            + MAX_FRAME_BYTES + "]");
        }
    }
}
