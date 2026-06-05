package com.hnieacm.problem.vo;

import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 判题节点 Token 校验结果
 */
@Data
public class JudgeNodeTokenValidationVo {

    private Boolean valid;

    private String nodeType;

    private String nodeId;

    private String tokenId;
}
