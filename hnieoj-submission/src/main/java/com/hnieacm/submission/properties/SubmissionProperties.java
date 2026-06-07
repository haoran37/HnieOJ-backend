package com.hnieacm.submission.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

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

    private JudgeOutbox judgeOutbox = new JudgeOutbox();

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
}
