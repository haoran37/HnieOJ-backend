package com.hnieacm.submission.service;

import com.hnieacm.common.dto.JudgeTaskAccessRequest;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/19
 * @Description: 判题任务级访问校验：签名令牌 + 租约/题目绑定在同一事务内完成。
 */
public interface JudgeTaskAccessService {

    /**
     * 校验测试数据下载资格：签名 nonce 单次消费、node/key/epoch 权威、租约与 problem 绑定一致。
     *
     * @param request problem 透传的原始签名上下文
     */
    void validateDownloadAccess(JudgeTaskAccessRequest request);
}
