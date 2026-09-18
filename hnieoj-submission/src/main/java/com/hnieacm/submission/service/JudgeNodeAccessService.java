package com.hnieacm.submission.service;

import com.hnieacm.judge.vo.JudgeNodeIdentity;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 判题节点访问校验服务
 */
public interface JudgeNodeAccessService {

    void checkAccess(String judgeToken, String authorizationHeader);

    JudgeNodeIdentity resolveIdentity(String judgeToken, String authorizationHeader);

    boolean hasActiveNodeForMode(String judgeMode);
}
