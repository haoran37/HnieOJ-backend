package com.hnieacm.user.controller;

import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.user.dto.ProfileChangeCreateRequest;
import com.hnieacm.user.dto.UpdatePasswordRequest;
import com.hnieacm.user.dto.UpdateUserProfileRequest;
import com.hnieacm.user.service.ProfileChangeService;
import com.hnieacm.user.service.UserManageService;
import com.hnieacm.user.service.UserMessageService;
import com.hnieacm.user.service.UserProfileService;
import com.hnieacm.user.vo.ProfileChangeVo;
import com.hnieacm.user.vo.UserMessageVo;
import com.hnieacm.user.vo.UserProfileVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 本人接口登录态回归：匿名 401 且不触达 Service；登录后 uid 一律取自服务端会话，
 * 接口不接收 ownerUid 等可操纵参数。
 */
class UserEndpointsControllerAuthTest {

    private UserMessageService userMessageService;
    private ProfileChangeService profileChangeService;
    private UserProfileService userProfileService;

    private UserMessageController userMessageController;
    private ProfileChangeController profileChangeController;
    private UserProfileController userProfileController;

    @BeforeEach
    void setUp() {
        SaTokenContextMockUtil.clearContext();
        userMessageService = mock(UserMessageService.class);
        profileChangeService = mock(ProfileChangeService.class);
        userProfileService = mock(UserProfileService.class);
        userMessageController = new UserMessageController(userMessageService);
        profileChangeController = new ProfileChangeController(profileChangeService);
        userProfileController = new UserProfileController(userProfileService, mock(UserManageService.class));
    }

    @AfterEach
    void tearDown() {
        SaTokenContextMockUtil.clearContext();
    }

    @Test
    void anonymousIsRejectedAsNotLoginBeforeService() {
        SaTokenContextMockUtil.setMockContext(() -> {
            assertThatThrownBy(() -> userMessageController.list(1, 10, null))
                    .isInstanceOf(NotLoginException.class);
            assertThatThrownBy(userMessageController::unreadCount).isInstanceOf(NotLoginException.class);
            assertThatThrownBy(() -> userMessageController.markRead(1L)).isInstanceOf(NotLoginException.class);
            assertThatThrownBy(userMessageController::markAllRead).isInstanceOf(NotLoginException.class);
            assertThatThrownBy(() -> userMessageController.delete(1L)).isInstanceOf(NotLoginException.class);

            assertThatThrownBy(() -> profileChangeController.create(new ProfileChangeCreateRequest()))
                    .isInstanceOf(NotLoginException.class);
            assertThatThrownBy(() -> profileChangeController.list(1, 10))
                    .isInstanceOf(NotLoginException.class);

            assertThatThrownBy(userProfileController::getProfile).isInstanceOf(NotLoginException.class);
            assertThatThrownBy(() -> userProfileController.updateProfile(new UpdateUserProfileRequest()))
                    .isInstanceOf(NotLoginException.class);
            assertThatThrownBy(() -> userProfileController.updatePassword(new UpdatePasswordRequest()))
                    .isInstanceOf(NotLoginException.class);
        });
        verifyNoInteractions(userMessageService, profileChangeService, userProfileService);
    }

    @Test
    void messageOperationsUseSessionUidForOwnership() {
        when(userMessageService.listMyMessages("alice", 1, 10, null))
                .thenReturn(new PageVo<>(List.<UserMessageVo>of(), 0L));

        SaTokenContextMockUtil.setMockContext(() -> {
            StpUtil.login("alice");
            assertThat(userMessageController.list(1, 10, null).getData().getTotal()).isZero();
            userMessageController.unreadCount();
            userMessageController.markRead(7L);
            userMessageController.markAllRead();
            userMessageController.delete(8L);
        });

        verify(userMessageService).listMyMessages("alice", 1, 10, null);
        verify(userMessageService).countUnread("alice");
        verify(userMessageService).markRead("alice", 7L);
        verify(userMessageService).markAllRead("alice");
        verify(userMessageService).deleteMessage("alice", 8L);
    }

    @Test
    void profileChangeOperationsUseSessionUid() {
        ProfileChangeCreateRequest request = new ProfileChangeCreateRequest();
        request.setRealname("张三");

        SaTokenContextMockUtil.setMockContext(() -> {
            StpUtil.login("alice");
            profileChangeController.create(request);
            profileChangeController.list(1, 10);
        });

        verify(profileChangeService).createChangeRequest(same("alice"), same(request));
        verify(profileChangeService).listMyChangeRequests("alice", 1, 10);
    }

    @Test
    void profileOperationsUseSessionUidNotRequestFields() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setUsername("new-name");
        UpdatePasswordRequest passwordRequest = new UpdatePasswordRequest();
        passwordRequest.setOldPassword("old");
        passwordRequest.setNewPassword("new-pass");

        when(userProfileService.getCurrentUserProfile("alice")).thenReturn(new UserProfileVo());

        SaTokenContextMockUtil.setMockContext(() -> {
            StpUtil.login("alice");
            assertThat(userProfileController.getProfile().getData()).isNotNull();
            userProfileController.updateProfile(request);
            userProfileController.updatePassword(passwordRequest);
        });

        verify(userProfileService).getCurrentUserProfile("alice");
        verify(userProfileService).updateCurrentUserProfile("alice", request);
        verify(userProfileService).updatePassword("alice", passwordRequest);
    }
}
