package com.hnieacm.user.service;

import com.hnieacm.common.dto.PageVo;
import com.hnieacm.user.dto.BatchUidsRequest;
import com.hnieacm.user.dto.UserProfileChangeApplyRequest;
import com.hnieacm.user.vo.BatchOperationResultVo;
import com.hnieacm.user.vo.UserProfileChangeApplyVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 用户资料修改申请服务
 */
public interface UserProfileChangeService {

    UserProfileChangeApplyVo submit(String uid, UserProfileChangeApplyRequest request);

    PageVo<UserProfileChangeApplyVo> listMine(String uid, int page, int pageSize);

    PageVo<UserProfileChangeApplyVo> listForAdmin(int page, int pageSize, String uid, String status);

    void approve(String uid);

    void reject(String uid, String reason);

    BatchOperationResultVo batchApprove(BatchUidsRequest request);
}
