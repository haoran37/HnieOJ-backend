package com.hnieacm.problem.dto;

import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 判题节点 Token 内部校验请求
 */
@Data
public class ValidateJudgeNodeTokenRequest {

    private String judgeToken;

    private String bearerToken;
}
