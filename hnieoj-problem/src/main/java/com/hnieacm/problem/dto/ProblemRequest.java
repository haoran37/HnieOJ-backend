package com.hnieacm.problem.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 题目核心请求体（用于管理端新增/编辑）
 * <p>字段与 {@code hnieoj_problem_db.problem} 表保持一致</p>
 */
@Data
public class ProblemRequest {

    /**
     * 编辑题目时必填。
     */
    private Long id;

    @NotBlank(message = "problemCode不能为空")
    private String problemCode;

    @NotBlank(message = "title不能为空")
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

    @Valid
    private List<ProblemExampleRequest> examples;

    private String hint;

    private Integer difficulty;

    /**
     * 题目可见范围：1公开，2私有，3比赛。
     */
    @NotNull(message = "auth不能为空")
    private Integer auth;

    private Integer ioScore;

    private Boolean isRemote;

    private String source;

    private String spjCode;

    private String spjLanguage;

    private Integer spjTimeLimit;

    private Integer spjMemoryLimit;

    private Integer spjStackLimit;

    private Integer spjOutputLimit;

    private String spjProtocol;

    private Boolean isRemoveEndBlank;

    private Boolean openCaseResult;
}
