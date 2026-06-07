package com.hnieacm.submission.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 判题任务 outbox 展示对象
 */
@Data
public class JudgeTaskOutboxVo {

    private Long id;

    private String messageId;

    private String judgeTaskId;

    private String submissionId;

    private String status;

    private Integer retryCount;

    private Integer maxRetryCount;

    private LocalDateTime nextRetryTime;

    private LocalDateTime sentTime;

    private String lastError;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
