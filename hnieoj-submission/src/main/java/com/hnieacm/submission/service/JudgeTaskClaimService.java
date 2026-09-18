package com.hnieacm.submission.service;

import com.hnieacm.judge.vo.JudgeNodeIdentity;
import com.hnieacm.submission.dto.JudgeTaskLeaseRequest;
import com.hnieacm.submission.vo.JudgeTaskClaimVo;
import com.hnieacm.submission.vo.JudgeTaskLeaseVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/18
 * @Description: 判题任务领取与续租服务
 */
public interface JudgeTaskClaimService {

    JudgeTaskClaimVo claim(JudgeNodeIdentity identity);

    JudgeTaskLeaseVo renew(String submissionId, JudgeNodeIdentity identity, JudgeTaskLeaseRequest request);
}
