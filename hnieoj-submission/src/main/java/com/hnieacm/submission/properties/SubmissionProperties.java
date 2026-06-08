package com.hnieacm.submission.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 提交模块配置
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "hnieoj.submission")
public class SubmissionProperties {

    /**
     * 单次提交代码最大字节数，按 UTF-8 编码计算。
     */
    private Integer maxCodeBytes = 65536;

    private Integer maxCheckerBytes = 262144;

    private Integer maxInteractorBytes = 262144;

    private Integer maxMessagePayloadBytes = 1048576;

    private List<String> supportedJudgeModes = List.of("default");

    private JudgeOutbox judgeOutbox = new JudgeOutbox();

    private JudgeTimeout judgeTimeout = new JudgeTimeout();

    private RejudgeTask rejudgeTask = new RejudgeTask();

    @Data
    public static class JudgeOutbox {

        /**
         * 重试扫描间隔，单位毫秒。
         */
        private Long retryIntervalMs = 10000L;

        /**
         * 单次扫描最多处理的 outbox 数量。
         */
        private Integer retryBatchSize = 20;

        /**
         * 最大投递次数。
         */
        private Integer maxRetryCount = 10;

        /**
         * 每次失败后的下次重试间隔，单位秒。
         */
        private Long retryBackoffSeconds = 30L;

        /**
         * processing 状态超时时间，单位秒。
         */
        private Long processingTimeoutSeconds = 120L;
    }

    @Data
    public static class JudgeTimeout {

        /**
         * 是否启用卡住提交扫描。
         */
        private Boolean enabled = Boolean.TRUE;

        /**
         * 扫描间隔，单位毫秒。
         */
        private Long scanIntervalMs = 60000L;

        /**
         * 单次最多处理的提交数量。
         */
        private Integer batchSize = 100;

        /**
         * Pending 状态超时时间，单位秒。
         */
        private Long pendingTimeoutSeconds = 1800L;

        /**
         * Compiling/Running 状态超时时间，单位秒。
         */
        private Long activeTimeoutSeconds = 1800L;

        private Long sentPendingWarnSeconds = 3600L;
    }

    @Data
    public static class RejudgeTask {

        /**
         * 批量重判任务扫描间隔，单位毫秒。
         */
        private Long scanIntervalMs = 10000L;

        /**
         * 单个任务每轮最多处理的提交数量。
         */
        private Integer batchSize = 50;

        /**
         * 调度租约时长，单位秒；超过该时间未释放时允许其他实例接管。
         */
        private Long leaseSeconds = 300L;

        private Integer leaseRenewEvery = 10;
    }
}
