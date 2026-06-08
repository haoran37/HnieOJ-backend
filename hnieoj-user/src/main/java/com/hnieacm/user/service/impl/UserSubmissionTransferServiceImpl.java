package com.hnieacm.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.user.dto.InternalTransferSubmissionOwnerRequest;
import com.hnieacm.user.dto.TransferUserSubmissionsRequest;
import com.hnieacm.user.entity.UserInfo;
import com.hnieacm.user.feign.SubmissionInternalFeignClient;
import com.hnieacm.user.service.UserSubmissionTransferService;
import com.hnieacm.user.service.manager.UserInfoManager;
import com.hnieacm.user.vo.InternalTransferSubmissionOwnerVo;
import com.hnieacm.user.vo.TransferUserSubmissionsVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 用户提交记录归属转移服务实现
 */
@Service
@RequiredArgsConstructor
public class UserSubmissionTransferServiceImpl implements UserSubmissionTransferService {

    private final UserInfoManager userInfoManager;
    private final SubmissionInternalFeignClient submissionInternalFeignClient;

    @Override
    public TransferUserSubmissionsVo transfer(String sourceUid, TransferUserSubmissionsRequest request) {
        String normalizedSourceUid = StrUtil.trimToNull(sourceUid);
        String normalizedTargetUid = StrUtil.trimToNull(request == null ? null : request.getTargetUid());
        if (normalizedSourceUid == null || normalizedTargetUid == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "sourceUid 和 targetUid 不能为空");
        }
        if (Objects.equals(normalizedSourceUid, normalizedTargetUid)) {
            throw new BizException(ResultCode.BAD_REQUEST, "源用户和目标用户不能相同");
        }
        if (Boolean.TRUE.equals(request.getDeleteOriginal())) {
            throw new BizException(ResultCode.BAD_REQUEST, "当前仅支持转移提交归属，不支持删除原记录");
        }

        userInfoManager.getUserByUid(normalizedSourceUid);
        UserInfo targetUser = userInfoManager.getUserByUid(normalizedTargetUid);

        InternalTransferSubmissionOwnerRequest internalRequest = new InternalTransferSubmissionOwnerRequest();
        internalRequest.setSourceUid(normalizedSourceUid);
        internalRequest.setTargetUid(normalizedTargetUid);
        internalRequest.setTargetUsername(targetUser.getUsername());
        Result<InternalTransferSubmissionOwnerVo> result = submissionInternalFeignClient.transferOwner(internalRequest);
        if (result == null || result.getCode() != ResultCode.SUCCESS || result.getData() == null) {
            throw new BizException(ResultCode.INTERNAL_ERROR, result == null ? "转移提交归属失败" : result.getMsg());
        }

        TransferUserSubmissionsVo vo = new TransferUserSubmissionsVo();
        vo.setSourceUid(normalizedSourceUid);
        vo.setTargetUid(normalizedTargetUid);
        vo.setTransferredCount(result.getData().getTransferredCount());
        return vo;
    }
}
