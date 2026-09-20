package com.hnieacm.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.user.constant.NoticeStatusConstant;
import com.hnieacm.user.constant.NoticeTargetTypeConstant;
import com.hnieacm.user.dto.NoticeSaveRequest;
import com.hnieacm.user.entity.SysClass;
import com.hnieacm.user.entity.UserInfo;
import com.hnieacm.user.entity.UserMessage;
import com.hnieacm.user.entity.UserNotice;
import com.hnieacm.user.mapper.SysClassMapper;
import com.hnieacm.user.mapper.UserInfoMapper;
import com.hnieacm.user.mapper.UserMessageMapper;
import com.hnieacm.user.mapper.UserNoticeMapper;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 定向通知管理服务回归：目标校验、草稿保存、发布幂等/收件人快照、发布后不可编辑。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NoticeAdminServiceImplTest {

    @Mock
    private UserNoticeMapper userNoticeMapper;

    @Mock
    private UserMessageMapper userMessageMapper;

    @Mock
    private UserInfoMapper userInfoMapper;

    @Mock
    private SysClassMapper sysClassMapper;

    private NoticeAdminServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MyBatisPlusTestSupport.initTableInfo(UserNotice.class, UserMessage.class, UserInfo.class, SysClass.class);
    }

    @BeforeEach
    void setUp() {
        service = new NoticeAdminServiceImpl(
                userNoticeMapper, userMessageMapper, userInfoMapper, sysClassMapper, new ObjectMapper());
    }

    @Test
    void createRejectsUnknownTargetType() {
        NoticeSaveRequest request = request("hello", "body", "GROUP", List.of("u1"));

        assertThatThrownBy(() -> service.createNotice(request, "admin"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST);
        verify(userNoticeMapper, never()).insert(any(UserNotice.class));
    }

    @Test
    void createRejectsNonNumericClassTargets() {
        NoticeSaveRequest request = request("hello", "body", NoticeTargetTypeConstant.CLASSES, List.of("1", "abc"));

        assertThatThrownBy(() -> service.createNotice(request, "admin"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("班级ID");
        verify(userNoticeMapper, never()).insert(any(UserNotice.class));
    }

    @Test
    void createRejectsMissingUserTarget() {
        when(userInfoMapper.selectList(any())).thenReturn(List.of());
        NoticeSaveRequest request = request("hello", "body", NoticeTargetTypeConstant.USERS, List.of("u1"));

        assertThatThrownBy(() -> service.createNotice(request, "admin"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.USER_NOT_FOUND);
        verify(userNoticeMapper, never()).insert(any(UserNotice.class));
    }

    @Test
    void createDeduplicatesTargetsAndSavesDraft() {
        when(userInfoMapper.selectList(any())).thenReturn(List.of(user("u1"), user("u2")));
        NoticeSaveRequest request = request("hello", "body", NoticeTargetTypeConstant.USERS,
                List.of("u1", "u1", " u2 "));

        service.createNotice(request, "admin");

        ArgumentCaptor<UserNotice> captor = ArgumentCaptor.forClass(UserNotice.class);
        verify(userNoticeMapper).insert(captor.capture());
        UserNotice saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(NoticeStatusConstant.DRAFT);
        assertThat(saved.getCreatorUid()).isEqualTo("admin");
        assertThat(saved.getTargetSpec()).isEqualTo("[\"u1\",\"u2\"]");
        assertThat(saved.getPublishedAt()).isNull();
    }

    @Test
    void publishLocksNoticeRowAndWritesRecipientSnapshot() {
        UserNotice notice = notice(5L, NoticeTargetTypeConstant.CLASSES, "[\"1\",\"2\"]",
                NoticeStatusConstant.DRAFT);
        when(userNoticeMapper.selectOne(any())).thenReturn(notice);
        when(userInfoMapper.selectList(any())).thenReturn(List.of(user("cls1"), user("cls2")));

        service.publishNotice(5L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<UserNotice>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(userNoticeMapper).selectOne(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getTargetSql()).contains("FOR UPDATE");

        verify(userMessageMapper, times(2)).insert(any(UserMessage.class));
        assertThat(notice.getStatus()).isEqualTo(NoticeStatusConstant.PUBLISHED);
        assertThat(notice.getPublishedAt()).isNotNull();
        verify(userNoticeMapper).updateById(notice);
    }

    @Test
    void publishWithoutRecipientsFailsAndDoesNotMarkPublished() {
        UserNotice notice = notice(6L, NoticeTargetTypeConstant.USERS, "[\"u1\"]", NoticeStatusConstant.DRAFT);
        when(userNoticeMapper.selectOne(any())).thenReturn(notice);
        when(userInfoMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.publishNotice(6L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("没有有效收件人");

        verify(userMessageMapper, never()).insert(any(UserMessage.class));
        verify(userNoticeMapper, never()).updateById(any(UserNotice.class));
        assertThat(notice.getStatus()).isEqualTo(NoticeStatusConstant.DRAFT);
    }

    @Test
    void publishAlreadyPublishedIsIdempotent() {
        UserNotice notice = notice(7L, NoticeTargetTypeConstant.USERS, "[\"u1\"]", NoticeStatusConstant.PUBLISHED);
        when(userNoticeMapper.selectOne(any())).thenReturn(notice);

        service.publishNotice(7L);

        verify(userMessageMapper, never()).insert(any(UserMessage.class));
        verify(userNoticeMapper, never()).updateById(any(UserNotice.class));
    }

    @Test
    void updatePublishedNoticeIsRejectedAndLocksRowFirst() {
        UserNotice notice = notice(8L, NoticeTargetTypeConstant.USERS, "[\"u1\"]", NoticeStatusConstant.PUBLISHED);
        when(userNoticeMapper.selectOne(any())).thenReturn(notice);
        NoticeSaveRequest request = request("new", "new body", NoticeTargetTypeConstant.USERS, List.of("u1"));

        assertThatThrownBy(() -> service.updateNotice(8L, request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不可编辑");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<UserNotice>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(userNoticeMapper).selectOne(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getTargetSql()).contains("FOR UPDATE");
        verify(userNoticeMapper, never()).updateById(any(UserNotice.class));
    }

    @Test
    void updateAfterConcurrentPublishCannotOverwritePublishedState() {
        // 发布事务先提交（行锁释放后状态为 PUBLISHED），随后编辑事务必须看到 PUBLISHED 并拒绝，
        // 不能用旧草稿快照把状态/标题改回去。
        UserNotice notice = notice(12L, NoticeTargetTypeConstant.USERS, "[\"u1\"]", NoticeStatusConstant.DRAFT);
        when(userNoticeMapper.selectOne(any())).thenReturn(notice);
        when(userInfoMapper.selectList(any())).thenReturn(List.of(user("u1")));

        service.publishNotice(12L);
        assertThat(notice.getStatus()).isEqualTo(NoticeStatusConstant.PUBLISHED);

        // 清除发布调用的记录，只观察编辑事务本身。
        clearInvocations(userNoticeMapper);

        NoticeSaveRequest request = request("rolled back", "rolled back", NoticeTargetTypeConstant.USERS,
                List.of("u1"));
        assertThatThrownBy(() -> service.updateNotice(12L, request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不可编辑");

        assertThat(notice.getStatus()).isEqualTo(NoticeStatusConstant.PUBLISHED);
        assertThat(notice.getTitle()).isEqualTo("title");
        verify(userNoticeMapper, never()).updateById(any(UserNotice.class));
    }

    @Test
    void deleteDoesNotCascadeToDeliveredMessages() {
        UserNotice notice = notice(9L, NoticeTargetTypeConstant.USERS, "[\"u1\"]", NoticeStatusConstant.PUBLISHED);
        when(userNoticeMapper.selectOne(any())).thenReturn(notice);

        service.deleteNotice(9L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<UserNotice>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(userNoticeMapper).selectOne(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getTargetSql()).contains("FOR UPDATE");

        verify(userNoticeMapper).deleteById(9L);
        verify(userMessageMapper, never()).delete(any());
        verify(userMessageMapper, never()).deleteById(any(java.io.Serializable.class));
        verify(userMessageMapper, never()).deleteBatchIds(anyList());
    }

    @Test
    void listRejectsUnknownStatusAndOversizedPage() {
        assertThatThrownBy(() -> service.listNotices(1, 10, null, "ARCHIVED"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("status");
        assertThatThrownBy(() -> service.listNotices(1, 101, null, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("pageSize");
    }

    @Test
    void listReturnsPagedNotices() {
        UserNotice notice = notice(11L, NoticeTargetTypeConstant.USERS, "[\"u1\"]", NoticeStatusConstant.DRAFT);
        notice.setTitle("t");
        Page<UserNotice> page = new Page<>(1, 10);
        page.setRecords(List.of(notice));
        page.setTotal(1);
        when(userNoticeMapper.selectPage(any(), any())).thenReturn(page);

        var result = service.listNotices(1, 10, null, null);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getList()).hasSize(1);
        assertThat(result.getList().get(0).getId()).isEqualTo(11L);
    }

    private NoticeSaveRequest request(String title, String content, String targetType, List<String> targetIds) {
        NoticeSaveRequest request = new NoticeSaveRequest();
        request.setTitle(title);
        request.setContent(content);
        request.setTargetType(targetType);
        request.setTargetIds(targetIds);
        return request;
    }

    private UserNotice notice(Long id, String targetType, String spec, String status) {
        UserNotice notice = new UserNotice();
        notice.setId(id);
        notice.setTitle("title");
        notice.setContent("content");
        notice.setTargetType(targetType);
        notice.setTargetSpec(spec);
        notice.setStatus(status);
        notice.setCreatorUid("admin");
        return notice;
    }

    private UserInfo user(String uid) {
        UserInfo user = new UserInfo();
        user.setUid(uid);
        return user;
    }

    @SuppressWarnings("unused")
    private SysClass sysClass(Long id) {
        SysClass sysClass = new SysClass();
        sysClass.setId(id);
        return sysClass;
    }
}
