package com.hnieacm.training.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/28
 * @Description: 管理端创建/编辑题单请求参数
 */
@Data
public class AdminTrainingSaveRequest {

    @NotBlank(message = "title 不能为空")
    private String title;

    @NotBlank(message = "type 不能为空")
    private String type;

    private String auth;

    private String privatePwd;

    private String description;

    @NotNull(message = "status 不能为空")
    private Boolean status;

    @Min(value = 0, message = "rank 不能小于 0")
    private Integer rank;

    @Valid
    private List<AdminTrainingProblemRequest> problems;
}
