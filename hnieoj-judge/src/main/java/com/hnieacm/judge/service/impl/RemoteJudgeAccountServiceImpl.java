package com.hnieacm.judge.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.judge.constant.SystemConfigConstant;
import com.hnieacm.judge.entity.RemoteJudgeAccount;
import com.hnieacm.judge.mapper.RemoteJudgeAccountMapper;
import com.hnieacm.judge.service.RemoteJudgeAccountService;
import com.hnieacm.judge.vo.RemoteJudgeAccountVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/03/01
 * @Description: 远程评测账号服务实现
 */
@Service
@RequiredArgsConstructor
public class RemoteJudgeAccountServiceImpl implements RemoteJudgeAccountService {

    private final RemoteJudgeAccountMapper remoteJudgeAccountMapper;

    /**
     * @MethodName listRemoteJudgeAccounts
     * @Param oj
     * @Param status
     * @Description 远程评测账户列表
     * @Return @return {@link List }<{@link RemoteJudgeAccountVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/03/01
     */
    @Override
    public List<RemoteJudgeAccountVo> listRemoteJudgeAccounts(String oj, Integer status) {
        if (status != null
                && !SystemConfigConstant.ENABLED_STATUS.equals(status)
                && !SystemConfigConstant.DISABLED_STATUS.equals(status)) {
            throw new BizException(ResultCode.BAD_REQUEST, "status 仅支持 0 或 1");
        }

        LambdaQueryWrapper<RemoteJudgeAccount> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(oj)) {
            wrapper.eq(RemoteJudgeAccount::getOj, oj.trim());
        }
        if (status != null) {
            wrapper.eq(RemoteJudgeAccount::getStatus, status);
        }
        wrapper.orderByDesc(RemoteJudgeAccount::getId);

        return remoteJudgeAccountMapper.selectList(wrapper).stream().map(account -> {
            RemoteJudgeAccountVo vo = new RemoteJudgeAccountVo();
            vo.setId(account.getId());
            vo.setOj(account.getOj());
            vo.setUsername(account.getUsername());
            vo.setStatus(account.getStatus());
            vo.setMaxConcurrency(account.getMaxConcurrency());
            vo.setGmtCreate(account.getGmtCreate());
            return vo;
        }).toList();
    }
}
