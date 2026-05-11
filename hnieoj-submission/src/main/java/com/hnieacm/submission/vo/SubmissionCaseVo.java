package com.hnieacm.submission.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/11
 * @Description: 提交测试点结果
 */
@Data
public class SubmissionCaseVo {

    private String caseId;

    private Integer status;

    private String statusText;

    private Integer time;

    private Integer memory;

    private Integer score;

    private String inputData;

    private String outputData;

    private String userOutput;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
