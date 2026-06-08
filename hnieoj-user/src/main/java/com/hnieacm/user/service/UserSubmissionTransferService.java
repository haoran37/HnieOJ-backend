package com.hnieacm.user.service;

import com.hnieacm.user.dto.TransferUserSubmissionsRequest;
import com.hnieacm.user.vo.TransferUserSubmissionsVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 用户提交记录归属转移服务
 */
public interface UserSubmissionTransferService {

    TransferUserSubmissionsVo transfer(String sourceUid, TransferUserSubmissionsRequest request);
}
