package com.hnieacm.submission.dto;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 判题节点结果事件请求
 */
@Data
public class JudgeResultEventRequest {

    private String eventType;

    private String submissionId;

    private String judgeTaskId;

    /** 当前执行尝试 ID，用于所有权/会话纪元 fencing；终态事件必填。 */
    private String attemptId;

    private Integer status;

    private String statusText;

    private Integer totalCase;

    private Integer judgedCase;

    private Integer currentCase;

    private Integer score;

    private CaseResult caseResult;

    private String message;

    private String diagnosticMessage;

    private OffsetDateTime eventTime;

    @Data
    public static class CaseResult {

        private String caseId;

        private Integer status;

        private String statusText;

        private Long time;

        private Long memory;

        private Integer score;

        private String userOutput;
    }
}
