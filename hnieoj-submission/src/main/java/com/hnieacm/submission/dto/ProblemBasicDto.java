package com.hnieacm.submission.dto;

import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 最小题目信息（来自题目服务内部 API）
 */
@Data
public class ProblemBasicDto {

    private Long id;
    private String problemCode;
    private String title;
    private Integer auth;
    private Integer type;
    private String judgeMode;
    private Integer timeLimit;
    private Integer memoryLimit;
    private Integer stackLimit;
    private String spjCode;
    private String spjLanguage;
    private Integer spjTimeLimit;
    private Integer spjMemoryLimit;
    private Integer spjStackLimit;
    private Integer spjOutputLimit;
    private String spjProtocol;
    private Integer ioScore;
    private Boolean isRemoveEndBlank;
    private Integer dataVersion;
    private Boolean hasTestdata;
    private Integer testdataCaseCount;
}
