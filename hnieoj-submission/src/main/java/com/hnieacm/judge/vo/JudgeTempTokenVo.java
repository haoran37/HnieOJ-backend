package com.hnieacm.judge.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 临时判题节点 Token 响应
 */
@Data
public class JudgeTempTokenVo {

    private String token;

    private String tokenType;

    private String nodeId;

    private String tokenId;

    private String fingerprintHash;

    private LocalDateTime expireTime;
}
