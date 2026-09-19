package com.hnieacm.problem.service;

import com.hnieacm.common.dto.JudgeTaskAccessRequest;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 判题节点访问校验服务（统一 NODE_ACCESS + Ed25519 签名上下文，经内部授权 Feign 到 submission）
 */
public interface JudgeNodeAccessService {

    /**
     * 校验判题节点对测试数据的访问资格。
     *
     * @param request 原始方法/路径/体摘要/签名上下文与任务绑定
     */
    void checkAccess(JudgeTaskAccessRequest request);
}
