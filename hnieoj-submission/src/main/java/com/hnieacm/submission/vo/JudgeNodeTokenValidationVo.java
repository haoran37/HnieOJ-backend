package com.hnieacm.submission.vo;

import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 判题节点 Token 校验结果
 */
@Data
public class JudgeNodeTokenValidationVo {

    private Boolean valid;

    private String nodeType;

    private String nodeId;

    private String tokenId;
}
