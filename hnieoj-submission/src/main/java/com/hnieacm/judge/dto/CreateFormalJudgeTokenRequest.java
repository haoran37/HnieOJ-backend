package com.hnieacm.judge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/18
 * @Description: 管理员签发正式判题节点独立凭证请求
 */
@Data
public class CreateFormalJudgeTokenRequest {

    @NotBlank(message = "nodeName 不能为空")
    private String nodeName;

    @NotNull(message = "maxConcurrency 不能为空")
    @Min(value = 1, message = "maxConcurrency 必须大于 0")
    @Max(value = 10000, message = "maxConcurrency 过大")
    private Integer maxConcurrency;

    private List<String> supportedJudgeModes;
}
