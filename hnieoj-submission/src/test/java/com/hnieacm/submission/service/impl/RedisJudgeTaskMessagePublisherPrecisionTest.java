package com.hnieacm.submission.service.impl;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/18
 * @Description: 首次 outbox 重试时间的 DATETIME(0) 精度对齐确定性用例（.800000/.900000）
 */
class RedisJudgeTaskMessagePublisherPrecisionTest {

    @Test
    void alignedScheduleStaysDueWhenNowHasHalfSecondFraction() {
        // 09:00:00.800：旧写法（直接使用 now）会被 DATETIME(0) 进位到 09:00:01
        LocalDateTime now = LocalDateTime.of(2026, 9, 18, 9, 0, 0, 800_000_000);
        LocalDateTime scheduled = RedisJudgeTaskMessagePublisher.alignedInitialNextRetryTime(now);

        assertThat(scheduled.getNano()).isZero();
        // 对同秒稍后的 now（09:00:00.900）仍应立即到期
        assertThat(scheduled).isBeforeOrEqualTo(LocalDateTime.of(2026, 9, 18, 9, 0, 0, 900_000_000));
    }

    @Test
    void unalignedFractionDemonstratesRegression() {
        // 直接使用 .800 写入 DATETIME(0) 会进位为 09:00:01，与 09:00:00.900 比较时判定为未到期
        LocalDateTime rawNow = LocalDateTime.of(2026, 9, 18, 9, 0, 0, 800_000_000);
        LocalDateTime roundedByDatabase = rawNow.withNano(0).plusSeconds(1);

        assertThat(roundedByDatabase).isAfter(LocalDateTime.of(2026, 9, 18, 9, 0, 0, 900_000_000));
    }
}
