package com.hnieacm.judge.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.judge.dto.RemoteJudgeAccountSaveRequest;
import com.hnieacm.judge.entity.RemoteJudgeAccount;
import com.hnieacm.judge.mapper.RemoteJudgeAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 远程评测账号服务测试
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class RemoteJudgeAccountServiceImplTest {

    private RemoteJudgeAccountMapper remoteJudgeAccountMapper;

    private RemoteJudgeAccountServiceImpl service;

    @BeforeEach
    void setUp() {
        remoteJudgeAccountMapper = mock(RemoteJudgeAccountMapper.class);
        service = new RemoteJudgeAccountServiceImpl(remoteJudgeAccountMapper);
    }

    @Test
    void shouldAddRemoteJudgeAccount() {
        when(remoteJudgeAccountMapper.insert(any(RemoteJudgeAccount.class))).thenReturn(1);

        service.addRemoteJudgeAccount(request("Codeforces", "bot", "pwd"));

        verify(remoteJudgeAccountMapper).insert(org.mockito.ArgumentMatchers.<RemoteJudgeAccount>argThat(account ->
                "Codeforces".equals(account.getOj())
                        && "bot".equals(account.getUsername())
                        && "pwd".equals(account.getPassword())
                        && Integer.valueOf(1).equals(account.getStatus())
                        && Integer.valueOf(2).equals(account.getMaxConcurrency())
        ));
    }

    @Test
    void shouldRejectAddWithoutPassword() {
        RemoteJudgeAccountSaveRequest request = request("Codeforces", "bot", null);

        assertThatThrownBy(() -> service.addRemoteJudgeAccount(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("password");
    }

    @Test
    void shouldUpdateRemoteJudgeAccountWithoutPasswordChange() {
        when(remoteJudgeAccountMapper.selectById(1)).thenReturn(new RemoteJudgeAccount());
        when(remoteJudgeAccountMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        service.updateRemoteJudgeAccount(1, request("AtCoder", "bot2", null));

        verify(remoteJudgeAccountMapper).update(any(), any(Wrapper.class));
    }

    @Test
    void shouldDeleteRemoteJudgeAccount() {
        when(remoteJudgeAccountMapper.selectById(1)).thenReturn(new RemoteJudgeAccount());
        when(remoteJudgeAccountMapper.deleteById(1)).thenReturn(1);

        service.deleteRemoteJudgeAccount(1);

        verify(remoteJudgeAccountMapper).deleteById(1);
    }

    private RemoteJudgeAccountSaveRequest request(String oj, String username, String password) {
        RemoteJudgeAccountSaveRequest request = new RemoteJudgeAccountSaveRequest();
        request.setOj(oj);
        request.setUsername(username);
        request.setPassword(password);
        request.setStatus(1);
        request.setMaxConcurrency(2);
        return request;
    }
}
