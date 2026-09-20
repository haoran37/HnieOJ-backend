package com.hnieacm.judge.service.impl;

import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.judge.dto.RemoteJudgeAccountCreateRequest;
import com.hnieacm.judge.dto.RemoteJudgeAccountUpdateRequest;
import com.hnieacm.judge.entity.RemoteJudgeAccount;
import com.hnieacm.judge.mapper.RemoteJudgeAccountMapper;
import com.hnieacm.judge.vo.RemoteJudgeAccountVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 远程账号 CRUD 回归：trim 账号、密码保留/原样保存、唯一冲突、404、VO/DTO 隐私。
 */
class RemoteJudgeAccountServiceImplTest {

    private RemoteJudgeAccountMapper mapper;
    private RemoteJudgeAccountServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(RemoteJudgeAccountMapper.class);
        service = new RemoteJudgeAccountServiceImpl(mapper);
    }

    private RemoteJudgeAccount existing() {
        RemoteJudgeAccount account = new RemoteJudgeAccount();
        account.setId(7);
        account.setOj("codeforces");
        account.setUsername("alice");
        account.setPassword("old-pass");
        account.setStatus(1);
        account.setMaxConcurrency(2);
        return account;
    }

    @Test
    void createTrimsOjAndUsernameButKeepsPasswordVerbatim() {
        when(mapper.selectCount(any())).thenReturn(0L);
        RemoteJudgeAccountCreateRequest request = new RemoteJudgeAccountCreateRequest();
        request.setOj("  codeforces  ");
        request.setUsername("  alice  ");
        request.setPassword("  p@ss  ");
        request.setStatus(1);
        request.setMaxConcurrency(3);

        service.createRemoteJudgeAccount(request);

        ArgumentCaptor<RemoteJudgeAccount> captor = ArgumentCaptor.forClass(RemoteJudgeAccount.class);
        verify(mapper).insert(captor.capture());
        RemoteJudgeAccount inserted = captor.getValue();
        assertThat(inserted.getOj()).isEqualTo("codeforces");
        assertThat(inserted.getUsername()).isEqualTo("alice");
        // 非空密码不 trim
        assertThat(inserted.getPassword()).isEqualTo("  p@ss  ");
        assertThat(inserted.getStatus()).isEqualTo(1);
        assertThat(inserted.getMaxConcurrency()).isEqualTo(3);
    }

    @Test
    void createRejectsBlankPassword() {
        RemoteJudgeAccountCreateRequest request = new RemoteJudgeAccountCreateRequest();
        request.setOj("codeforces");
        request.setUsername("alice");
        request.setPassword("   ");

        assertThatThrownBy(() -> service.createRemoteJudgeAccount(request))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST);
        verify(mapper, never()).insert(any(RemoteJudgeAccount.class));
    }

    @Test
    void createRejectsDuplicateOjUsername() {
        when(mapper.selectCount(any())).thenReturn(1L);
        RemoteJudgeAccountCreateRequest request = new RemoteJudgeAccountCreateRequest();
        request.setOj("codeforces");
        request.setUsername("alice");
        request.setPassword("secret");

        assertThatThrownBy(() -> service.createRemoteJudgeAccount(request))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST);
        verify(mapper, never()).insert(any(RemoteJudgeAccount.class));
    }

    @Test
    void createRejectsBadStatusConcurrencyAndLength() {
        RemoteJudgeAccountCreateRequest badStatus = validCreateRequest();
        badStatus.setStatus(2);
        assertThatThrownBy(() -> service.createRemoteJudgeAccount(badStatus)).isInstanceOf(BizException.class);

        RemoteJudgeAccountCreateRequest badConcurrency = validCreateRequest();
        badConcurrency.setMaxConcurrency(0);
        assertThatThrownBy(() -> service.createRemoteJudgeAccount(badConcurrency)).isInstanceOf(BizException.class);
        badConcurrency.setMaxConcurrency(101);
        assertThatThrownBy(() -> service.createRemoteJudgeAccount(badConcurrency)).isInstanceOf(BizException.class);

        RemoteJudgeAccountCreateRequest overlongOj = validCreateRequest();
        overlongOj.setOj("o".repeat(21));
        assertThatThrownBy(() -> service.createRemoteJudgeAccount(overlongOj)).isInstanceOf(BizException.class);
    }

    @Test
    void updateMissingPasswordKeepsOriginal() {
        when(mapper.selectById(7)).thenReturn(existing());
        when(mapper.selectCount(any())).thenReturn(0L);

        RemoteJudgeAccountUpdateRequest request = new RemoteJudgeAccountUpdateRequest();
        request.setUsername("  bob  ");

        service.updateRemoteJudgeAccount(7, request);

        ArgumentCaptor<RemoteJudgeAccount> captor = ArgumentCaptor.forClass(RemoteJudgeAccount.class);
        verify(mapper).updateById(captor.capture());
        RemoteJudgeAccount updated = captor.getValue();
        assertThat(updated.getUsername()).isEqualTo("bob");
        // 只更新显式提供的字段：密码未提供则根本不 set，交由 DB 保留原值
        assertThat(updated.getPassword()).isNull();
        assertThat(updated.getOj()).isNull();
        assertThat(updated.getStatus()).isNull();
        assertThat(updated.getMaxConcurrency()).isNull();
    }

    @Test
    void updateBlankPasswordKeepsOriginal() {
        when(mapper.selectById(7)).thenReturn(existing());
        when(mapper.selectCount(any())).thenReturn(0L);

        RemoteJudgeAccountUpdateRequest request = new RemoteJudgeAccountUpdateRequest();
        request.setStatus(0);
        request.setPassword("   ");

        service.updateRemoteJudgeAccount(7, request);

        ArgumentCaptor<RemoteJudgeAccount> captor = ArgumentCaptor.forClass(RemoteJudgeAccount.class);
        verify(mapper).updateById(captor.capture());
        RemoteJudgeAccount updated = captor.getValue();
        // 空白密码不 set，DB 中原密码不会被回写覆盖；其它显式字段照常更新
        assertThat(updated.getPassword()).isNull();
        assertThat(updated.getStatus()).isEqualTo(0);
    }

    @Test
    void blankPasswordEditAfterPasswordChangeDoesNotRevertNewPassword() {
        when(mapper.selectById(7)).thenReturn(existing());
        when(mapper.selectCount(any())).thenReturn(0L);

        // 第一次编辑：改密码
        RemoteJudgeAccountUpdateRequest changePassword = new RemoteJudgeAccountUpdateRequest();
        changePassword.setPassword("new-secret");
        service.updateRemoteJudgeAccount(7, changePassword);

        ArgumentCaptor<RemoteJudgeAccount> firstUpdate = ArgumentCaptor.forClass(RemoteJudgeAccount.class);
        verify(mapper).updateById(firstUpdate.capture());
        assertThat(firstUpdate.getValue().getPassword()).isEqualTo("new-secret");

        // 第二次编辑基于旧快照（selectById 仍返回 old-pass），只改状态且密码留空：
        // 更新对象不含 password，已改为 new-secret 的密码不会被 old-pass 覆盖。
        reset(mapper);
        when(mapper.selectById(7)).thenReturn(existing());
        when(mapper.selectCount(any())).thenReturn(0L);

        RemoteJudgeAccountUpdateRequest blankPassword = new RemoteJudgeAccountUpdateRequest();
        blankPassword.setStatus(0);
        blankPassword.setPassword("   ");
        service.updateRemoteJudgeAccount(7, blankPassword);

        ArgumentCaptor<RemoteJudgeAccount> secondUpdate = ArgumentCaptor.forClass(RemoteJudgeAccount.class);
        verify(mapper).updateById(secondUpdate.capture());
        assertThat(secondUpdate.getValue().getPassword()).isNull();
        assertThat(secondUpdate.getValue().getStatus()).isEqualTo(0);
    }

    @Test
    void updateNonBlankPasswordIsStoredVerbatim() {
        when(mapper.selectById(7)).thenReturn(existing());
        when(mapper.selectCount(any())).thenReturn(0L);

        RemoteJudgeAccountUpdateRequest request = new RemoteJudgeAccountUpdateRequest();
        request.setPassword("  new-secret  ");
        request.setStatus(0);
        request.setMaxConcurrency(100);

        service.updateRemoteJudgeAccount(7, request);

        ArgumentCaptor<RemoteJudgeAccount> captor = ArgumentCaptor.forClass(RemoteJudgeAccount.class);
        verify(mapper).updateById(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("  new-secret  ");
        assertThat(captor.getValue().getStatus()).isEqualTo(0);
        assertThat(captor.getValue().getMaxConcurrency()).isEqualTo(100);
    }

    @Test
    void updateUnknownIdReturnsNotFound() {
        when(mapper.selectById(999)).thenReturn(null);

        assertThatThrownBy(() -> service.updateRemoteJudgeAccount(999, new RemoteJudgeAccountUpdateRequest()))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.NOT_FOUND);
    }

    @Test
    void updateRejectsDuplicateOjUsername() {
        when(mapper.selectById(7)).thenReturn(existing());
        when(mapper.selectCount(any())).thenReturn(1L);

        RemoteJudgeAccountUpdateRequest request = new RemoteJudgeAccountUpdateRequest();
        request.setUsername("taken");

        assertThatThrownBy(() -> service.updateRemoteJudgeAccount(7, request))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST);
        verify(mapper, never()).updateById(any(RemoteJudgeAccount.class));
    }

    @Test
    void deleteUnknownIdReturnsNotFound() {
        when(mapper.selectById(999)).thenReturn(null);

        assertThatThrownBy(() -> service.deleteRemoteJudgeAccount(999))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.NOT_FOUND);
        verify(mapper, never()).deleteById(any(java.io.Serializable.class));
    }

    @Test
    void deleteExistingAccountDeletesRow() {
        when(mapper.selectById(7)).thenReturn(existing());

        service.deleteRemoteJudgeAccount(7);

        verify(mapper).deleteById(7);
    }

    @Test
    void accountVoAndDtoDoNotExposePassword() {
        assertThat(Arrays.stream(RemoteJudgeAccountVo.class.getDeclaredFields()).map(Field::getName))
                .doesNotContain("password");

        RemoteJudgeAccountCreateRequest create = new RemoteJudgeAccountCreateRequest();
        create.setPassword("super-secret-value");
        assertThat(create.toString()).doesNotContain("super-secret-value");

        RemoteJudgeAccountUpdateRequest update = new RemoteJudgeAccountUpdateRequest();
        update.setPassword("another-secret-value");
        assertThat(update.toString()).doesNotContain("another-secret-value");
    }

    @Test
    void listReturnsVoWithoutPassword() {
        when(mapper.selectList(any())).thenReturn(List.of(existing()));

        List<RemoteJudgeAccountVo> accounts = service.listRemoteJudgeAccounts(null, null);

        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).getUsername()).isEqualTo("alice");
        assertThat(Arrays.stream(accounts.get(0).getClass().getDeclaredFields()).map(Field::getName))
                .doesNotContain("password");
    }

    private RemoteJudgeAccountCreateRequest validCreateRequest() {
        RemoteJudgeAccountCreateRequest request = new RemoteJudgeAccountCreateRequest();
        request.setOj("codeforces");
        request.setUsername("alice");
        request.setPassword("secret");
        return request;
    }
}
