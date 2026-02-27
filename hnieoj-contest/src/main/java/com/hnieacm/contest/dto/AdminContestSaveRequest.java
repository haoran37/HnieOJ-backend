package com.hnieacm.contest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/27
 * @Description: 新增/编辑比赛请求参数
 */
@Data
public class AdminContestSaveRequest {

    @NotBlank(message = "title 不能为空")
    private String title;

    @NotBlank(message = "type 不能为空")
    private String type;

    @NotBlank(message = "auth 不能为空")
    private String auth;

    private String source;

    private List<String> customTags;

    @NotNull(message = "status 不能为空")
    private Boolean status;

    @NotNull(message = "startTime 不能为空")
    private Long startTime;

    @NotNull(message = "endTime 不能为空")
    private Long endTime;

    private String description;

    private String rankShowName;

    private Boolean openRank;

    private Boolean sealRank;

    private Long sealRankTime;

    @Valid
    private List<AdminContestProblemRequest> problems;

    private List<String> accountList;
}
