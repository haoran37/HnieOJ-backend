package com.hnieacm.auth.service;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 注册审核服务
 */
public interface RegistrationReviewService {

    /**
     * 通过注册申请
     */
    void approve(String uid);

    /**
     * 驳回注册申请
     */
    void reject(String uid, String reason);

    /**
     * 批量通过注册申请
     *
     * @return 统计结果文案，例如：成功: x, 失败: y
     */
    String batchApprove(List<String> uids);
}

