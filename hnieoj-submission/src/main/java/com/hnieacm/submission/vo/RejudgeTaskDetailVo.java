package com.hnieacm.submission.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/08
 * @Description: Rejudge task detail item.
 */
@Data
public class RejudgeTaskDetailVo {

    private String runId;

    private String submissionId;

    private String uid;

    private String username;

    private String originalStatus;

    private String currentStatus;

    private Integer status;

    private Integer originalStatusCode;

    private Integer currentStatusCode;

    private String language;

    private Integer score;

    private Integer originalScore;

    private Integer currentScore;

    private Integer time;

    private Integer originalTime;

    private Integer currentTime;

    private Integer memory;

    private Integer originalMemory;

    private Integer currentMemory;

    private LocalDateTime submitTime;

    private LocalDateTime finishedTime;
}
