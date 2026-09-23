package com.hnieacm.contest.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.contest.constant.ContestAuthConstant;
import com.hnieacm.contest.constant.ContestRegisterTypeConstant;
import com.hnieacm.contest.entity.Contest;
import com.hnieacm.contest.entity.ContestRegister;
import com.hnieacm.contest.mapper.ContestMapper;
import com.hnieacm.contest.mapper.ContestRegisterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * @author HnieOJ contributors
 */
@Service
@RequiredArgsConstructor
public class ContestRegistrationService {
    private final ContestMapper contestMapper;
    private final ContestRegisterMapper registerMapper;

    public boolean isRegistered(long contestId, String uid) {
        return registerMapper.selectCount(new LambdaQueryWrapper<ContestRegister>()
                .eq(ContestRegister::getCid, contestId).eq(ContestRegister::getUid, uid)
                .eq(ContestRegister::getStatus, 1)) > 0;
    }

    public void register(long contestId, String uid) {
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null || !Integer.valueOf(1).equals(contest.getIsVisible())) {
            throw new BizException(ResultCode.NOT_FOUND, "比赛不存在或不可见");
        }
        if (!LocalDateTime.now().isBefore(contest.getEndTime())) {
            throw new BizException(ResultCode.FORBIDDEN, "比赛已结束，不能报名");
        }
        if (isRegistered(contestId, uid)) {
            return;
        }
        if (contest.getAuth() != null && contest.getAuth() == ContestAuthConstant.PRIVATE) {
            throw new BizException(ResultCode.FORBIDDEN, "邀请制比赛由管理员添加参赛账号");
        }
        ContestRegister registration = new ContestRegister();
        registration.setCid(contestId);
        registration.setUid(uid);
        registration.setStatus(1);
        registration.setType(ContestRegisterTypeConstant.USER);
        try {
            registerMapper.insert(registration);
        } catch (DuplicateKeyException ignored) {
            // The unique (cid, uid) key makes concurrent registration idempotent.
        }
    }
}
