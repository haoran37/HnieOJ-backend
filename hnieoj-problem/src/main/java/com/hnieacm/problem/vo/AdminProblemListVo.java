package com.hnieacm.problem.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 管理员题目列表项
 */
@Data
public class AdminProblemListVo {

    private Long id;

    private String problemCode;

    private String title;

    private String author;

    private Integer auth;

    private Integer type;

    private Integer difficulty;

    private List<String> tags;

    private Integer submissionCount;

    private Integer acceptedCount;

    private BigDecimal scorePercentage;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}


