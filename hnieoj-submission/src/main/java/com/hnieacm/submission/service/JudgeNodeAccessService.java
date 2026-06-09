package com.hnieacm.submission.service;

import com.hnieacm.common.dto.JudgeNodeRequestContext;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 判题节点访问校验服务
 */
public interface JudgeNodeAccessService {

    void checkAccess(String judgeToken, String authorizationHeader, JudgeNodeRequestContext requestContext);

    boolean hasActiveNodeForMode(String judgeMode);
}
