package com.hnieacm.judge.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.judge.constant.SystemConfigConstant;
import com.hnieacm.judge.dto.RemoteJudgeAccountCreateRequest;
import com.hnieacm.judge.dto.RemoteJudgeAccountUpdateRequest;
import com.hnieacm.judge.entity.RemoteJudgeAccount;
import com.hnieacm.judge.mapper.RemoteJudgeAccountMapper;
import com.hnieacm.judge.service.RemoteJudgeAccountService;
import com.hnieacm.judge.vo.RemoteJudgeAccountVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/03/01
 * @Description: 远程评测账号服务实现：复用原表做账号 CRUD，唯一性由 (oj, username) 唯一索引兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RemoteJudgeAccountServiceImpl implements RemoteJudgeAccountService {

    private static final int MAX_OJ_LENGTH = 20;
    private static final int MAX_USERNAME_LENGTH = 100;
    private static final int MAX_PASSWORD_LENGTH = 255;
    private static final int MIN_CONCURRENCY = 1;
    private static final int MAX_CONCURRENCY = 100;

    private final RemoteJudgeAccountMapper remoteJudgeAccountMapper;

    /**
     * @MethodName listRemoteJudgeAccounts
     * @Param oj
     * @Param status
     * @Description 远程评测账户列表；VO 不包含密码
     * @Return @return {@link List }<{@link RemoteJudgeAccountVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/03/01
     */
    @Override
    public List<RemoteJudgeAccountVo> listRemoteJudgeAccounts(String oj, Integer status) {
        validateStatus(status);

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createRemoteJudgeAccount(RemoteJudgeAccountCreateRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "账号参数不能为空");
        }
        String oj = requireTrimmed(request.getOj(), MAX_OJ_LENGTH, "oj");
        String username = requireTrimmed(request.getUsername(), MAX_USERNAME_LENGTH, "username");
        String password = requirePassword(request.getPassword());
        validateStatus(request.getStatus());
        validateConcurrency(request.getMaxConcurrency());

        if (existsAccount(oj, username, null)) {
            throw new BizException(ResultCode.BAD_REQUEST, "该 OJ 账号已存在");
        }

        RemoteJudgeAccount account = new RemoteJudgeAccount();
        account.setOj(oj);
        account.setUsername(username);
        account.setPassword(password);
        account.setStatus(request.getStatus());
        account.setMaxConcurrency(request.getMaxConcurrency());
        try {
            remoteJudgeAccountMapper.insert(account);
        } catch (DuplicateKeyException e) {
            // 依赖 (oj, username) 唯一索引兜底并发创建
            throw new BizException(ResultCode.BAD_REQUEST, "该 OJ 账号已存在");
        }
        log.info("Remote judge account created, id: {}, oj: {}", account.getId(), oj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRemoteJudgeAccount(Integer id, RemoteJudgeAccountUpdateRequest request) {
        if (id == null || id <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "id 不合法");
        }
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "账号参数不能为空");
        }

        RemoteJudgeAccount existed = remoteJudgeAccountMapper.selectById(id);
        if (existed == null) {
            throw new BizException(ResultCode.NOT_FOUND, "账号不存在");
        }

        // 只更新请求中显式提供的字段：留空字段（尤其密码）根本不 set，
        // 避免用旧快照回写覆盖并发请求刚更新的密码。
        RemoteJudgeAccount update = new RemoteJudgeAccount();
        update.setId(id);
        boolean hasUpdate = false;

        String oj = existed.getOj();
        if (request.getOj() != null) {
            oj = requireTrimmed(request.getOj(), MAX_OJ_LENGTH, "oj");
            update.setOj(oj);
            hasUpdate = true;
        }
        String username = existed.getUsername();
        if (request.getUsername() != null) {
            username = requireTrimmed(request.getUsername(), MAX_USERNAME_LENGTH, "username");
            update.setUsername(username);
            hasUpdate = true;
        }

        if (StrUtil.isNotBlank(request.getPassword())) {
            update.setPassword(requirePassword(request.getPassword()));
            hasUpdate = true;
        }

        validateStatus(request.getStatus());
        if (request.getStatus() != null) {
            update.setStatus(request.getStatus());
            hasUpdate = true;
        }

        validateConcurrency(request.getMaxConcurrency());
        if (request.getMaxConcurrency() != null) {
            update.setMaxConcurrency(request.getMaxConcurrency());
            hasUpdate = true;
        }

        if (existsAccount(oj, username, id)) {
            throw new BizException(ResultCode.BAD_REQUEST, "该 OJ 账号已存在");
        }

        if (!hasUpdate) {
            // 全部字段缺省/空白：无需落库，密码等原值保持不变。
            log.info("Remote judge account update is a no-op, id: {}", id);
            return;
        }

        try {
            remoteJudgeAccountMapper.updateById(update);
        } catch (DuplicateKeyException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "该 OJ 账号已存在");
        }
        log.info("Remote judge account updated, id: {}, oj: {}", id, oj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRemoteJudgeAccount(Integer id) {
        if (id == null || id <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "id 不合法");
        }
        RemoteJudgeAccount existed = remoteJudgeAccountMapper.selectById(id);
        if (existed == null) {
            throw new BizException(ResultCode.NOT_FOUND, "账号不存在");
        }
        remoteJudgeAccountMapper.deleteById(id);
        log.info("Remote judge account deleted, id: {}", id);
    }

    private boolean existsAccount(String oj, String username, Integer excludeId) {
        LambdaQueryWrapper<RemoteJudgeAccount> wrapper = new LambdaQueryWrapper<RemoteJudgeAccount>()
                .eq(RemoteJudgeAccount::getOj, oj)
                .eq(RemoteJudgeAccount::getUsername, username);
        if (excludeId != null) {
            wrapper.ne(RemoteJudgeAccount::getId, excludeId);
        }
        Long count = remoteJudgeAccountMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    private String requireTrimmed(String rawValue, int maxLength, String field) {
        String value = StrUtil.trim(rawValue);
        if (StrUtil.isBlank(value)) {
            throw new BizException(ResultCode.BAD_REQUEST, field + " 不能为空");
        }
        if (value.length() > maxLength) {
            throw new BizException(ResultCode.BAD_REQUEST, field + " 长度不能超过 " + maxLength);
        }
        return value;
    }

    private String requirePassword(String rawPassword) {
        if (StrUtil.isBlank(rawPassword)) {
            throw new BizException(ResultCode.BAD_REQUEST, "password 不能为空");
        }
        if (rawPassword.length() > MAX_PASSWORD_LENGTH) {
            throw new BizException(ResultCode.BAD_REQUEST, "password 长度不能超过 " + MAX_PASSWORD_LENGTH);
        }
        // 不 trim 实际非空密码，原样保存
        return rawPassword;
    }

    private void validateStatus(Integer status) {
        if (status != null
                && !SystemConfigConstant.ENABLED_STATUS.equals(status)
                && !SystemConfigConstant.DISABLED_STATUS.equals(status)) {
            throw new BizException(ResultCode.BAD_REQUEST, "status 仅支持 0 或 1");
        }
    }

    private void validateConcurrency(Integer maxConcurrency) {
        if (maxConcurrency == null) {
            return;
        }
        if (maxConcurrency < MIN_CONCURRENCY || maxConcurrency > MAX_CONCURRENCY) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "maxConcurrency 必须为 " + MIN_CONCURRENCY + " 到 " + MAX_CONCURRENCY);
        }
    }
}
