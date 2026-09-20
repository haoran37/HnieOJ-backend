package com.hnieacm.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.user.entity.UserMessage;
import com.hnieacm.user.mapper.UserMessageMapper;
import com.hnieacm.user.support.MyBatisPlusTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 本人收件箱服务回归：uid 归属过滤、未读统计排除已删除、读/删幂等、非本人 404。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserMessageServiceImplTest {

    @Mock
    private UserMessageMapper userMessageMapper;

    private UserMessageServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MyBatisPlusTestSupport.initTableInfo(UserMessage.class);
    }

    @BeforeEach
    void setUp() {
        service = new UserMessageServiceImpl(userMessageMapper);
    }

    @Test
    void listFiltersByLoginUidAndExcludesDeleted() {
        Page<UserMessage> page = new Page<>(1, 10);
        page.setRecords(List.of(message(1L, "u1", null, null)));
        page.setTotal(1);
        when(userMessageMapper.selectPage(any(), any())).thenReturn(page);

        var result = service.listMyMessages("u1", 1, 10, false);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getList()).hasSize(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<UserMessage>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(userMessageMapper).selectPage(any(), captor.capture());
        String sql = captor.getValue().getTargetSql();
        assertThat(sql).contains("recipient_uid");
        assertThat(sql).contains("deleted_at IS NULL");
    }

    @Test
    void listUnreadAddsReadAtCondition() {
        Page<UserMessage> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);
        when(userMessageMapper.selectPage(any(), any())).thenReturn(page);

        service.listMyMessages("u1", 1, 10, true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<UserMessage>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(userMessageMapper).selectPage(any(), captor.capture());
        assertThat(captor.getValue().getTargetSql()).contains("read_at IS NULL");
    }

    @Test
    void unreadCountExcludesDeleted() {
        when(userMessageMapper.selectCount(any())).thenReturn(3L);

        assertThat(service.countUnread("u1")).isEqualTo(3L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<UserMessage>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(userMessageMapper).selectCount(captor.capture());
        String sql = captor.getValue().getTargetSql();
        assertThat(sql).contains("recipient_uid");
        assertThat(sql).contains("deleted_at IS NULL");
        assertThat(sql).contains("read_at IS NULL");
    }

    @Test
    void markReadIsIdempotentForOwnMessage() {
        UserMessage alreadyRead = message(1L, "u1", LocalDateTime.now(), null);
        when(userMessageMapper.selectOne(any())).thenReturn(alreadyRead);
        service.markRead("u1", 1L);
        verify(userMessageMapper, never()).updateById(any(UserMessage.class));

        UserMessage unread = message(2L, "u1", null, null);
        when(userMessageMapper.selectOne(any())).thenReturn(unread);
        service.markRead("u1", 2L);
        verify(userMessageMapper).updateById(unread);
        assertThat(unread.getReadAt()).isNotNull();
    }

    @Test
    void markReadOtherUserReturns404() {
        when(userMessageMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.markRead("u1", 99L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.NOT_FOUND);
        verify(userMessageMapper, never()).updateById(any(UserMessage.class));
    }

    @Test
    void deleteIsSoftAndIdempotent() {
        UserMessage active = message(3L, "u1", null, null);
        when(userMessageMapper.selectOne(any())).thenReturn(active);
        service.deleteMessage("u1", 3L);
        verify(userMessageMapper, times(1)).updateById(active);
        assertThat(active.getDeletedAt()).isNotNull();

        UserMessage alreadyDeleted = message(4L, "u1", null, LocalDateTime.now());
        when(userMessageMapper.selectOne(any())).thenReturn(alreadyDeleted);
        service.deleteMessage("u1", 4L);
        // 已删除的消息不再二次写库，但调用仍成功（幂等）。
        verify(userMessageMapper, never()).updateById(alreadyDeleted);
    }

    @Test
    void deleteOtherUserReturns404() {
        when(userMessageMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.deleteMessage("u1", 100L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.NOT_FOUND);
    }

    @Test
    void markAllReadOnlyTargetsOwnUnread() {
        service.markAllRead("u1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<UserMessage>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(userMessageMapper).update(nullable(UserMessage.class), captor.capture());
        String sql = captor.getValue().getTargetSql();
        assertThat(sql).contains("recipient_uid");
        assertThat(sql).contains("deleted_at IS NULL");
        assertThat(sql).contains("read_at IS NULL");
    }

    private UserMessage message(Long id, String recipientUid, LocalDateTime readAt, LocalDateTime deletedAt) {
        UserMessage message = new UserMessage();
        message.setId(id);
        message.setRecipientUid(recipientUid);
        message.setNoticeId(1L);
        message.setTitle("title");
        message.setContent("content");
        message.setReadAt(readAt);
        message.setDeletedAt(deletedAt);
        return message;
    }
}
