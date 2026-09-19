package com.hnieacm.submission.service;

import com.hnieacm.judge.service.SignedCaller;
import com.hnieacm.submission.dto.JudgeTaskLeaseRequest;
import com.hnieacm.submission.dto.JudgeTaskResumeRequest;
import com.hnieacm.submission.vo.JudgeTaskClaimVo;
import com.hnieacm.submission.vo.JudgeTaskLeaseVo;
import com.hnieacm.submission.vo.JudgeTaskResumeVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/18
 * @Description: 判题任务领取、续租与会话恢复服务。
 *
 * <p>所有方法都要求已认证的 {@link SignedCaller}（nodeId/keyId/accessVersion/sessionEpoch），
 * 并在节点行锁内以数据库权威额度与所有权接受或拒绝。</p>
 */
public interface JudgeTaskClaimService {

    /**
     * 领取一个可执行的判题任务；额度已满、排空中或无可用任务时返回 null。
     *
     * @param caller 已认证节点会话
     * @return 任务与租约；无可用任务时为 null
     */
    JudgeTaskClaimVo claim(SignedCaller caller);

    /**
     * 续租当前会话持有的任务。
     *
     * @param submissionId 提交展示 ID
     * @param caller       已认证节点会话
     * @param request      续租请求（judgeTaskId/attemptId）
     * @return 新的租约到期时间
     */
    JudgeTaskLeaseVo renew(String submissionId, SignedCaller caller, JudgeTaskLeaseRequest request);

    /**
     * 节点重连后恢复未完成任务：合法未过期的同一 attempt 迁移到当前会话且不增加执行预算；
     * 已完成的 attempt 返回 completed=true 供结果重放对账；过期 attempt 与不匹配所有权一律拒绝。
     *
     * @param request 待恢复 attempt 列表
     * @param caller  新会话的已认证身份
     * @return 恢复与拒绝明细
     */
    JudgeTaskResumeVo resume(JudgeTaskResumeRequest request, SignedCaller caller);
}
