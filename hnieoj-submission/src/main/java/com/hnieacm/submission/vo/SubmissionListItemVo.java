package com.hnieacm.submission.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/11
 * @Description: 提交记录列表项
 */
@Data
public class SubmissionListItemVo {

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

    private LocalDateTime gmtCreate;
}
