package com.hnieacm.judge.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.judge.constant.SystemConfigConstant;
import com.hnieacm.judge.dto.RemoteJudgeAccountSaveRequest;
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

    /**
     * @MethodName addRemoteJudgeAccount
     * @Param request
     * @Description 新增远程评测账号
     * @Return @return void
     * @Author HaoRan_Lyu
     * @Date 2026/06/09
     */
    @Override
    public void addRemoteJudgeAccount(RemoteJudgeAccountSaveRequest request) {
        validateRequest(request, true);

        RemoteJudgeAccount account = new RemoteJudgeAccount();
        account.setOj(trim(request.getOj()));
        account.setUsername(trim(request.getUsername()));
        account.setPassword(trim(request.getPassword()));
        account.setStatus(request.getStatus());
        account.setMaxConcurrency(request.getMaxConcurrency());
        int inserted = remoteJudgeAccountMapper.insert(account);
        if (inserted <= 0) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "添加远程评测账号失败");
        }
    }

    /**
     * @MethodName updateRemoteJudgeAccount
     * @Param id
     * @Param request
     * @Description 更新远程评测账号
     * @Return @return void
     * @Author HaoRan_Lyu
     * @Date 2026/06/09
     */
    @Override
    public void updateRemoteJudgeAccount(Integer id, RemoteJudgeAccountSaveRequest request) {
        if (id == null || id <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "id 不合法");
        }
        validateRequest(request, false);
        ensureExists(id);

        UpdateWrapper<RemoteJudgeAccount> wrapper = new UpdateWrapper<RemoteJudgeAccount>()
                .eq("id", id)
                .set("oj", trim(request.getOj()))
                .set("username", trim(request.getUsername()))
                .set("status", request.getStatus())
                .set("max_concurrency", request.getMaxConcurrency());
        if (StrUtil.isNotBlank(request.getPassword())) {
            wrapper.set("password", trim(request.getPassword()));
        }

        int updated = remoteJudgeAccountMapper.update(null, wrapper);
        if (updated <= 0) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "更新远程评测账号失败");
        }
    }

    /**
     * @MethodName deleteRemoteJudgeAccount
     * @Param id
     * @Description 删除远程评测账号
     * @Return @return void
     * @Author HaoRan_Lyu
     * @Date 2026/06/09
     */
    @Override
    public void deleteRemoteJudgeAccount(Integer id) {
        if (id == null || id <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "id 不合法");
        }
        ensureExists(id);
        int deleted = remoteJudgeAccountMapper.deleteById(id);
        if (deleted <= 0) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "删除远程评测账号失败");
        }
    }

    private void validateRequest(RemoteJudgeAccountSaveRequest request, boolean requirePassword) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        if (StrUtil.isBlank(request.getOj())) {
            throw new BizException(ResultCode.BAD_REQUEST, "oj 不能为空");
        }
        if (StrUtil.isBlank(request.getUsername())) {
            throw new BizException(ResultCode.BAD_REQUEST, "username 不能为空");
        }
        if (requirePassword && StrUtil.isBlank(request.getPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST, "password 不能为空");
        }
        if (request.getMaxConcurrency() == null || request.getMaxConcurrency() < 1) {
            throw new BizException(ResultCode.BAD_REQUEST, "maxConcurrency 最小为 1");
        }
        if (!SystemConfigConstant.ENABLED_STATUS.equals(request.getStatus())
                && !SystemConfigConstant.DISABLED_STATUS.equals(request.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "status 仅支持 0 或 1");
        }
    }

    private void ensureExists(Integer id) {
        if (remoteJudgeAccountMapper.selectById(id) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "远程评测账号不存在");
        }
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
