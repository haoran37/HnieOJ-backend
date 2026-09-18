package com.hnieacm.common.properties;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/18
 * @Description: 判题任务 Redis Streams 分发配置；启动即校验有界与相互关系，非法配置直接失败
 */
@Data
@Component
@ConfigurationProperties(prefix = "hnieoj.judge.stream")
public class JudgeStreamProperties {

    private static final String DEFAULT_STREAM_KEY = "hnieoj:judge:task:default";
    private static final String DEFAULT_SPJ_STREAM_KEY = "hnieoj:judge:task:spj";
    private static final String DEFAULT_INTERACTIVE_STREAM_KEY = "hnieoj:judge:task:interactive";
    private static final String DEFAULT_CONSUMER_GROUP = "hnieoj-judge-gateway";
    private static final int MAX_BATCH = 1000;
    private static final int MAX_ATTEMPT_COUNT = 100;
    private static final long MAX_LEASE_SECONDS = 86400L;
    private static final long MAX_DEADLINE_SECONDS = 604800L;
    private static final long MAX_INTERVAL_MILLIS = 86400000L;

    /**
     * default 模式固定 Stream key，节点不得自行指定。
     */
    private String defaultStreamKey = DEFAULT_STREAM_KEY;

    /**
     * spj 模式固定 Stream key。
     */
    private String spjStreamKey = DEFAULT_SPJ_STREAM_KEY;

    /**
     * interactive 模式固定 Stream key。
     */
    private String interactiveStreamKey = DEFAULT_INTERACTIVE_STREAM_KEY;

    /**
     * 网关侧消费组名。
     */
    private String consumerGroup = DEFAULT_CONSUMER_GROUP;

    /**
     * 单次 claim 最多读取的条目数。
     */
    private Integer claimBatchSize = 4;

    /**
     * 单次 claim 为跳过错配消息最多重复的次数，避免请求长时间占用线程。
     */
    private Integer claimMaxRounds = 4;

    /**
     * 单次租约时长，单位秒。
     */
    private Long leaseSeconds = 60L;

    /**
     * 距离租约到期多久提示节点续期，单位毫秒；必须小于租约时长。
     */
    private Long renewAfterMillis = 20000L;

    /**
     * 单次执行硬截止时间，单位秒；到期后不得再续租。
     */
    private Long executionDeadlineSeconds = 7200L;

    /**
     * 单次任务最大实际领取执行次数。
     */
    private Integer maxAttemptCount = 3;

    /**
     * PEL 孤儿回收的最小空闲时间，单位毫秒。
     */
    private Long orphanPendingMinIdleMillis = 300000L;

    /**
     * 单轮回收/清理处理的条目上限。
     */
    private Integer recoveryBatchSize = 100;

    /**
     * 单轮回收处理的最大活跃租约任务数。
     */
    private Integer recoveryLeaseBatchSize = 50;

    /**
     * 长时间停留在 queued 且无有效租约的判定阈值，单位秒；用于 Redis 丢消息/清空后的补偿重派。
     */
    private Long strandedQueuedSeconds = 3600L;

    /**
     * 恢复扫描间隔，单位毫秒。
     */
    private Long recoveryScanIntervalMs = 60000L;

    /**
     * @MethodName validate
     * @Description 启动时校验所有有界配置与相互关系，非法配置直接失败而不是静默回退魔法值
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    @PostConstruct
    public void validate() {
        requireRange(claimBatchSize, "claimBatchSize", 1, MAX_BATCH);
        requireRange(claimMaxRounds, "claimMaxRounds", 1, MAX_BATCH);
        requireRange(leaseSeconds, "leaseSeconds", 1, MAX_LEASE_SECONDS);
        requireRange(executionDeadlineSeconds, "executionDeadlineSeconds", 1, MAX_DEADLINE_SECONDS);
        requireRange(maxAttemptCount, "maxAttemptCount", 1, MAX_ATTEMPT_COUNT);
        requireRange(recoveryBatchSize, "recoveryBatchSize", 1, MAX_BATCH);
        requireRange(recoveryLeaseBatchSize, "recoveryLeaseBatchSize", 1, MAX_BATCH);
        requireRange(orphanPendingMinIdleMillis, "orphanPendingMinIdleMillis", 1, MAX_INTERVAL_MILLIS);
        requireRange(strandedQueuedSeconds, "strandedQueuedSeconds", 1, MAX_DEADLINE_SECONDS);
        requireRange(recoveryScanIntervalMs, "recoveryScanIntervalMs", 1, MAX_INTERVAL_MILLIS);
        requireNonBlank(defaultStreamKey, "defaultStreamKey");
        requireNonBlank(spjStreamKey, "spjStreamKey");
        requireNonBlank(interactiveStreamKey, "interactiveStreamKey");
        requireNonBlank(consumerGroup, "consumerGroup");
        if (defaultStreamKey.equals(spjStreamKey) || defaultStreamKey.equals(interactiveStreamKey)
                || spjStreamKey.equals(interactiveStreamKey)) {
            throw new IllegalStateException("hnieoj.judge.stream Stream key 必须互不相同");
        }
        if (renewAfterMillis == null || renewAfterMillis <= 0) {
            throw new IllegalStateException("hnieoj.judge.stream.renew-after-millis 必须为正数");
        }
        if (renewAfterMillis >= leaseSeconds * 1000L) {
            throw new IllegalStateException("hnieoj.judge.stream.renew-after-millis 必须小于 lease-seconds*1000");
        }
    }

    private void requireRange(Number value, String name, long min, long max) {
        if (value == null || value.longValue() < min || value.longValue() > max) {
            throw new IllegalStateException("hnieoj.judge.stream." + name + " 必须在 [" + min + ", " + max + "] 范围内");
        }
    }

    private void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("hnieoj.judge.stream." + name + " 不能为空");
        }
    }
}
