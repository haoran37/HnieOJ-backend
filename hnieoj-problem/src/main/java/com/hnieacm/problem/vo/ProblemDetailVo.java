package com.hnieacm.problem.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 公共题目详情
 */
@Data
public class ProblemDetailVo {

    private Long id;

    private String problemCode;

    private String title;

    private String author;

    private Integer type;

    private String judgeMode;

    private Integer timeLimit;

    private Integer memoryLimit;

    private Integer stackLimit;

    private String description;

    private String input;

    private String output;

    private List<ProblemExampleVo> examples;

    private String hint;

    private Integer difficulty;

    private Integer ioScore;

    private Boolean isRemote;

    private String source;

    private Boolean openCaseResult;

    private BigDecimal scorePercentage;

    private Integer submissionCount;

    private Integer acceptedCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime gmtCreate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime gmtModified;
}
