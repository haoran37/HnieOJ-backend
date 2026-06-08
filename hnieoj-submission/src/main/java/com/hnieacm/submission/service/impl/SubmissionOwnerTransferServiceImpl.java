package com.hnieacm.submission.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.submission.dto.TransferSubmissionOwnerRequest;
import com.hnieacm.submission.entity.Judge;
import com.hnieacm.submission.mapper.JudgeMapper;
import com.hnieacm.submission.service.SubmissionOwnerTransferService;
import com.hnieacm.submission.vo.TransferSubmissionOwnerVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 提交记录归属转移服务实现
 */
@Service
@RequiredArgsConstructor
public class SubmissionOwnerTransferServiceImpl implements SubmissionOwnerTransferService {

    private final JudgeMapper judgeMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferSubmissionOwnerVo transfer(TransferSubmissionOwnerRequest request) {
        String sourceUid = StrUtil.trimToNull(request == null ? null : request.getSourceUid());
        String targetUid = StrUtil.trimToNull(request == null ? null : request.getTargetUid());
        String targetUsername = StrUtil.trimToNull(request == null ? null : request.getTargetUsername());
        if (sourceUid == null || targetUid == null || targetUsername == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "sourceUid、targetUid 和 targetUsername 不能为空");
        }
        if (Objects.equals(sourceUid, targetUid)) {
            throw new BizException(ResultCode.BAD_REQUEST, "源用户和目标用户不能相同");
        }

        int updated = judgeMapper.update(null, new LambdaUpdateWrapper<Judge>()
                .eq(Judge::getUid, sourceUid)
                .set(Judge::getUid, targetUid)
                .set(Judge::getUsername, targetUsername));
        TransferSubmissionOwnerVo vo = new TransferSubmissionOwnerVo();
        vo.setSourceUid(sourceUid);
        vo.setTargetUid(targetUid);
        vo.setTransferredCount(updated);
        return vo;
    }
}
