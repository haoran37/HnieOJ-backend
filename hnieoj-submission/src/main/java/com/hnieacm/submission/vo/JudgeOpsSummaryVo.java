package com.hnieacm.submission.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: Judge operation summary.
 */
@Data
public class JudgeOpsSummaryVo {

    private LocalDateTime generatedAt;

    private Map<String, Long> outboxStatusCounts;

    private Long abnormalOutboxCount;

    private Long exhaustedOutboxCount;

    private Long retryableOutboxCount;

    private Map<Integer, Long> judgingStatusCounts;

    private Long judgingSubmissionCount;

    private Long stalePendingCount;

    private Long staleActiveCount;

    private Long longPendingWarnCount;

    private Long pendingTimeoutSeconds;

    private Long activeTimeoutSeconds;

    private Long longPendingWarnSeconds;

    private Boolean healthy;

    private List<String> warnings;
}
