package com.hnieacm.submission.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 重判任务展示对象
 */
@Data
public class RejudgeTaskVo {

    private Long id;

    private Long problemId;

    private String problemCode;

    private Long contestId;

    private LocalDateTime rangeStart;

    private LocalDateTime rangeEnd;

    private String status;

    private Integer totalCount;

    private Integer processedCount;

    private Integer failedCount;

    private Long lastJudgeId;

    private String lastError;

    private String lockedBy;

    private LocalDateTime lockUntil;

    private String adminId;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
