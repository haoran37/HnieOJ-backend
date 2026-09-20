package com.hnieacm.user.controller;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.user.dto.NoticeSaveRequest;
import com.hnieacm.user.service.NoticeAdminService;
import com.hnieacm.user.vo.UserNoticeListVo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 管理通知控制器显式鉴权回归：hnieoj-user 未注册 SaInterceptor，
 * 控制器须直接以 StpUtil 拒绝匿名(401)/普通用户(403)，且管理员操作者 uid 取自服务端登录态。
 */
class AdminNoticeControllerAuthTest {

    private static StpInterface originalStpInterface;

    private NoticeAdminService noticeAdminService;
    private AdminNoticeController controller;

    @BeforeAll
    static void setUpRoles() {
        originalStpInterface = SaManager.getStpInterface();
        SaManager.setStpInterface(new StpInterface() {
            @Override
            public List<String> getPermissionList(Object loginId, String loginType) {
                return List.of();
            }

            @Override
            public List<String> getRoleList(Object loginId, String loginType) {
                return switch (String.valueOf(loginId)) {
                    case "admin" -> List.of(RoleConstant.ADMIN);
                    case "root" -> List.of(RoleConstant.ROOT);
                    case "student" -> List.of(RoleConstant.STUDENT);
                    default -> List.of();
                };
            }
        });
    }

    @AfterAll
    static void restoreRoles() {
        SaManager.setStpInterface(originalStpInterface);
    }

    @BeforeEach
    void setUp() {
        SaTokenContextMockUtil.clearContext();
        noticeAdminService = mock(NoticeAdminService.class);
        controller = new AdminNoticeController(noticeAdminService);
    }

    @AfterEach
    void tearDown() {
        SaTokenContextMockUtil.clearContext();
    }

    @Test
    void anonymousIsRejectedAsNotLoginBeforeService() {
        SaTokenContextMockUtil.setMockContext(() -> {
            assertThatThrownBy(() -> controller.list(1, 10, null, null))
                    .isInstanceOf(NotLoginException.class);
            assertThatThrownBy(() -> controller.detail(1L)).isInstanceOf(NotLoginException.class);
            assertThatThrownBy(() -> controller.create(new NoticeSaveRequest()))
                    .isInstanceOf(NotLoginException.class);
            assertThatThrownBy(() -> controller.update(1L, new NoticeSaveRequest()))
                    .isInstanceOf(NotLoginException.class);
            assertThatThrownBy(() -> controller.delete(1L)).isInstanceOf(NotLoginException.class);
            assertThatThrownBy(() -> controller.publish(1L)).isInstanceOf(NotLoginException.class);
        });
        verifyNoInteractions(noticeAdminService);
    }

    @Test
    void loggedInStudentIsForbiddenBeforeService() {
        SaTokenContextMockUtil.setMockContext(() -> {
            StpUtil.login("student");
            assertThatThrownBy(() -> controller.list(1, 10, null, null))
                    .isInstanceOf(NotRoleException.class);
            assertThatThrownBy(() -> controller.detail(1L)).isInstanceOf(NotRoleException.class);
            assertThatThrownBy(() -> controller.create(new NoticeSaveRequest()))
                    .isInstanceOf(NotRoleException.class);
            assertThatThrownBy(() -> controller.update(1L, new NoticeSaveRequest()))
                    .isInstanceOf(NotRoleException.class);
            assertThatThrownBy(() -> controller.delete(1L)).isInstanceOf(NotRoleException.class);
            assertThatThrownBy(() -> controller.publish(1L)).isInstanceOf(NotRoleException.class);
        });
        verifyNoInteractions(noticeAdminService);
    }

    @Test
    void adminPassesAndCreatorUidComesFromSession() {
        NoticeSaveRequest request = new NoticeSaveRequest();
        request.setTitle("t");
        request.setContent("c");
        request.setTargetType("USERS");
        request.setTargetIds(List.of("u1"));

        SaTokenContextMockUtil.setMockContext(() -> {
            StpUtil.login("admin");
            when(noticeAdminService.createNotice(any(NoticeSaveRequest.class), eq("admin"))).thenReturn(5L);
            assertThat(controller.create(request).getData()).isEqualTo(5L);
        });

        verify(noticeAdminService).createNotice(request, "admin");
    }

    @Test
    void rootPassesForReads() {
        when(noticeAdminService.listNotices(1, 10, null, null))
                .thenReturn(new PageVo<>(List.<UserNoticeListVo>of(), 0L));

        SaTokenContextMockUtil.setMockContext(() -> {
            StpUtil.login("root");
            assertThat(controller.list(1, 10, null, null).getData().getTotal()).isZero();
        });

        verify(noticeAdminService).listNotices(1, 10, null, null);
    }
}
