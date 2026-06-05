package com.hnieacm.problem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 数据库实体类，对应表：problem
 */
@Data
@TableName("problem")
public class Problem {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("problem_code")
    private String problemCode;

    private String title;

    private String author;

    /**
     * 0: ACM, 1: OI.
     */
    private Integer type;

    private String judgeMode;

    private Integer timeLimit;

    private Integer memoryLimit;

    private Integer stackLimit;

    private String description;

    private String input;

    private String output;

    /**
     * JSON string.
     */
    private String examples;

    private String hint;

    private Integer difficulty;

    /**
     * 1: public, 2: private, 3: contest only.
     */
    private Integer auth;

    private Integer ioScore;

    private Boolean isRemote;

    private String source;

    private String spjCode;

    private String spjLanguage;

    private Boolean isRemoveEndBlank;

    private Boolean openCaseResult;

    private BigDecimal scorePercentage;

    private Integer submissionCount;

    private Integer acceptedCount;

    private Integer dataVersion;

    private String modifiedUser;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
