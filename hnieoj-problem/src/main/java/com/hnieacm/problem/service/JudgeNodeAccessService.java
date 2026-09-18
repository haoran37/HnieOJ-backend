package com.hnieacm.problem.service;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 判题节点访问校验服务
 */
public interface JudgeNodeAccessService {

    void checkAccess(String judgeToken, String authorizationHeader);

    /**
     * @MethodName checkTaskAccess
     * @Param problemId
     * @Param submissionId
     * @Param judgeTaskId
     * @Param attemptId
     * @Param judgeToken
     * @Param authorizationHeader
     * @Description 下载测试数据前校验节点身份与有效租约、任务绑定题目
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    void checkTaskAccess(Long problemId, String submissionId, String judgeTaskId, String attemptId,
                         String judgeToken, String authorizationHeader);
}
