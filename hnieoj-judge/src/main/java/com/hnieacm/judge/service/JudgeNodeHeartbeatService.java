package com.hnieacm.judge.service;

import com.hnieacm.judge.dto.JudgeNodeHeartbeatRequest;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 判题节点心跳服务
 */
public interface JudgeNodeHeartbeatService {

    void recordHeartbeat(String judgeToken, String authorizationHeader, JudgeNodeHeartbeatRequest request);

    boolean hasActiveNodeForMode(String judgeMode);
}
