package com.hnieacm.submission.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hnieacm.common.dto.JudgeTaskAccessRequest;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.judge.service.NodeSignedHttpService;
import com.hnieacm.judge.service.SignedCaller;
import com.hnieacm.submission.service.JudgeTaskAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/19
 * @Description: 测试数据下载资格校验实现：先验签，再在同一事务内做 node+task 行锁访问判定。
 *
 * <p>签名校验消费一次性 nonce；租约校验使用与任务访问相同的行锁边界，保证下载资格与
 * 所有权/题目绑定不可被并发轮换、吊销或错配绕过。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeTaskAccessServiceImpl implements JudgeTaskAccessService {

    private final NodeSignedHttpService nodeSignedHttpService;
    private final JudgeTaskLeaseManager judgeTaskLeaseManager;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void validateDownloadAccess(JudgeTaskAccessRequest request) {
        if (request == null || StrUtil.isBlank(request.getSubmissionId())
                || StrUtil.isBlank(request.getJudgeTaskId()) || StrUtil.isBlank(request.getAttemptId())
                || request.getProblemId() == null) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "submissionId/judgeTaskId/attemptId/problemId 不能为空");
        }
        SignedCaller caller = nodeSignedHttpService.authorizeContext(request);
        judgeTaskLeaseManager.validateDownloadAccess(request.getSubmissionId(), request.getJudgeTaskId(),
                request.getAttemptId(), request.getProblemId(), caller);
    }
}
