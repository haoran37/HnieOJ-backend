package com.hnieacm.judge.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 创建临时判题节点授权码请求
 */
@Data
public class CreateJudgeAuthCodeRequest {

    private String nodeName;

    private String remark;

    @NotNull(message = "expireSeconds 不能为空")
    @Min(value = 60, message = "expireSeconds 必须大于等于 60")
    private Long expireSeconds;

    @NotNull(message = "maxExchangeCount 不能为空")
    @Min(value = 1, message = "maxExchangeCount 必须大于等于 1")
    private Integer maxExchangeCount;
}
