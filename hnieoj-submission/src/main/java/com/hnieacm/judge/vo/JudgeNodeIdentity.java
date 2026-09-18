package com.hnieacm.judge.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/18
 * @Description: 校验后的判题节点身份，nodeId 只来自服务端验证结果
 */
@Data
public class JudgeNodeIdentity {

    private String nodeId;

    private String tokenId;

    private String nodeType;

    private Integer maxConcurrency;

    private Long runningTasks;

    private Boolean draining;

    private LocalDateTime authorizationUntil;

    private LocalDateTime expireTime;

    private List<String> supportedJudgeModes;
}
