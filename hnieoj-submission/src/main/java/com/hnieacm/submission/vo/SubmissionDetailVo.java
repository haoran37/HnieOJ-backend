package com.hnieacm.submission.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/11
 * @Description: 提交记录详情
 */
@Data
public class SubmissionDetailVo {

    private String submissionId;

    private String problemCode;

    private String uid;

    private String username;

    private String language;

    private Integer status;

    private String statusText;

    private Integer time;

    private Integer memory;

    private Integer score;

    private Long contestId;

    private Integer totalCase;

    private Integer judgedCase;

    private Integer currentCase;

    private String errorMessage;

    private String judger;

    private String code;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
