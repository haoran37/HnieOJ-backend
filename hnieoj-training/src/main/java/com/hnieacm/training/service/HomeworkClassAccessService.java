package com.hnieacm.training.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.training.entity.HomeworkClass;
import com.hnieacm.training.feign.HomeworkUserFeignClient;
import com.hnieacm.training.mapper.HomeworkClassMapper;
import com.hnieacm.training.vo.HomeworkUserVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Checks the caller's current class against a homework's assigned classes.
 * @author HnieOJ contributors
 */
@Service
@RequiredArgsConstructor
public class HomeworkClassAccessService {
    private final HomeworkClassMapper homeworkClassMapper;
    private final HomeworkUserFeignClient userClient;

    public Long currentClassId(String uid, String authorization) {
        if (uid == null || uid.isBlank() || authorization == null || authorization.isBlank()) {
            throw new BizException(ResultCode.FORBIDDEN, "无作业访问资格");
        }
        Result<HomeworkUserVo> response;
        try {
            response = userClient.getUserDetail(uid, authorization);
        } catch (RuntimeException exception) {
            throw new BizException(ResultCode.FORBIDDEN, "无法验证作业班级归属");
        }
        if (response == null || response.getCode() != ResultCode.SUCCESS || response.getData() == null
                || !uid.equals(response.getData().getUid()) || response.getData().getClassId() == null) {
            throw new BizException(ResultCode.FORBIDDEN, "无作业访问资格");
        }
        return response.getData().getClassId();
    }

    public void requireAssigned(long homeworkId, long classId) {
        if (homeworkClassMapper.selectCount(new LambdaQueryWrapper<HomeworkClass>()
                .eq(HomeworkClass::getHid, homeworkId)
                .eq(HomeworkClass::getClassId, classId)) == 0) {
            throw new BizException(ResultCode.FORBIDDEN, "作业未发布到所在班级");
        }
    }
}
