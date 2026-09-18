package com.hnieacm.submission.properties;

import com.hnieacm.common.properties.JudgeStreamProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/18
 * @Description: Redis Streams 配置校验：非法值/关系必须启动即失败，不能静默回退魔法值
 */
class JudgeStreamPropertiesValidationTest {

    @Test
    void defaultConfigurationIsValid() {
        assertThatCode(() -> new JudgeStreamProperties().validate()).doesNotThrowAnyException();
    }

    @Test
    void rejectsRenewAfterNotLessThanLease() {
        JudgeStreamProperties properties = new JudgeStreamProperties();
        properties.setLeaseSeconds(10L);
        properties.setRenewAfterMillis(10_000L);
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("renew-after-millis");
    }

    @Test
    void rejectsNonPositiveBatchSize() {
        JudgeStreamProperties properties = new JudgeStreamProperties();
        properties.setClaimBatchSize(0);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsOutOfRangeBatchSize() {
        JudgeStreamProperties properties = new JudgeStreamProperties();
        properties.setRecoveryBatchSize(100000);
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("recoveryBatchSize");
    }

    @Test
    void rejectsNonPositiveOrphanPendingMinIdle() {
        JudgeStreamProperties properties = new JudgeStreamProperties();
        properties.setOrphanPendingMinIdleMillis(0L);
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("orphanPendingMinIdleMillis");
    }

    @Test
    void rejectsDuplicateStreamKeys() {
        JudgeStreamProperties properties = new JudgeStreamProperties();
        properties.setSpjStreamKey(properties.getDefaultStreamKey());
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("互不相同");
    }

    @Test
    void rejectsBlankConsumerGroup() {
        JudgeStreamProperties properties = new JudgeStreamProperties();
        properties.setConsumerGroup(" ");
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("consumerGroup");
    }
}
