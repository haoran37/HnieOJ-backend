package com.hnieacm.judge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 临时判题节点兑换 Token 请求
 */
@Data
public class ExchangeJudgeTempTokenRequest {

    @NotBlank(message = "authCode 不能为空")
    private String authCode;

    private String nodeName;
}
