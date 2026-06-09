package com.hnieacm.problem.service;

import com.hnieacm.common.dto.JudgeNodeRequestContext;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 判题节点访问校验服务
 */
public interface JudgeNodeAccessService {

    void checkAccess(String judgeToken, String authorizationHeader, JudgeNodeRequestContext requestContext);
}
