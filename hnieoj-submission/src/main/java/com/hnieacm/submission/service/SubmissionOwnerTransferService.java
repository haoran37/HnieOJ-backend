package com.hnieacm.submission.service;

import com.hnieacm.submission.dto.TransferSubmissionOwnerRequest;
import com.hnieacm.submission.vo.TransferSubmissionOwnerVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 提交记录归属转移服务
 */
public interface SubmissionOwnerTransferService {

    TransferSubmissionOwnerVo transfer(TransferSubmissionOwnerRequest request);
}
