package com.hnieacm.training.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/28
 * @Description: 管理端新增/编辑作业请求参数
 */
@Data
public class AdminHomeworkSaveRequest {

    @NotBlank(message = "title 不能为空")
    private String title;

    private String source;

    @NotNull(message = "status 不能为空")
    private Boolean status;

    @NotNull(message = "startTime 不能为空")
    @Min(value = 1, message = "startTime 必须大于 0")
    private Long startTime;

    @NotNull(message = "endTime 不能为空")
    @Min(value = 1, message = "endTime 必须大于 0")
    private Long endTime;

    private String description;

    @NotEmpty(message = "classIds 不能为空")
    private List<@NotNull(message = "classId 不能为空") @Min(value = 1, message = "classId 必须大于等于 1") Long> classIds;

    @Valid
    private List<AdminHomeworkProblemRequest> problems;
}
