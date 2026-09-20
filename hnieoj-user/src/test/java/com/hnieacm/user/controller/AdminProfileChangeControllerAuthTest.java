package com.hnieacm.user.controller;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.user.dto.ProfileChangeReviewRequest;
import com.hnieacm.user.service.ProfileChangeService;
import com.hnieacm.user.vo.ProfileChangeVo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 身份资料审核控制器显式鉴权回归：匿名 401、普通用户 403、ADMIN/ROOT 通过，
 * 审核人 uid 取自服务端登录态而非请求体。
 */
class AdminProfileChangeControllerAuthTest {

    private static StpInterface originalStpInterface;

    private ProfileChangeService profileChangeService;
    private AdminProfileChangeController controller;

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
        profileChangeService = mock(ProfileChangeService.class);
        controller = new AdminProfileChangeController(profileChangeService);
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
            assertThatThrownBy(() -> controller.approve(1L, null)).isInstanceOf(NotLoginException.class);
            assertThatThrownBy(() -> controller.reject(1L, new ProfileChangeReviewRequest()))
                    .isInstanceOf(NotLoginException.class);
        });
        verifyNoInteractions(profileChangeService);
    }

    @Test
    void loggedInStudentIsForbiddenBeforeService() {
        SaTokenContextMockUtil.setMockContext(() -> {
            StpUtil.login("student");
            assertThatThrownBy(() -> controller.list(1, 10, null, null))
                    .isInstanceOf(NotRoleException.class);
            assertThatThrownBy(() -> controller.approve(1L, null)).isInstanceOf(NotRoleException.class);
            assertThatThrownBy(() -> controller.reject(1L, new ProfileChangeReviewRequest()))
                    .isInstanceOf(NotRoleException.class);
        });
        verifyNoInteractions(profileChangeService);
    }

    @Test
    void adminApprovesWithSessionReviewerUid() {
        ProfileChangeReviewRequest request = new ProfileChangeReviewRequest();
        request.setReason("同意");

        SaTokenContextMockUtil.setMockContext(() -> {
            StpUtil.login("admin");
            assertThat(controller.approve(7L, request).getCode()).isEqualTo(200);
        });

        verify(profileChangeService).approve(7L, "同意", "admin");
    }

    @Test
    void rootRejectsWithSessionReviewerUid() {
        ProfileChangeReviewRequest request = new ProfileChangeReviewRequest();
        request.setReason("材料不足");

        SaTokenContextMockUtil.setMockContext(() -> {
            StpUtil.login("root");
            assertThat(controller.reject(8L, request).getCode()).isEqualTo(200);
        });

        verify(profileChangeService).reject(8L, "材料不足", "root");
    }

    @Test
    void adminCanListPendingRequests() {
        when(profileChangeService.listAdminChangeRequests(1, 10, "PENDING", null))
                .thenReturn(new PageVo<>(List.<ProfileChangeVo>of(), 0L));

        SaTokenContextMockUtil.setMockContext(() -> {
            StpUtil.login("admin");
            assertThat(controller.list(1, 10, "PENDING", null).getData().getTotal()).isZero();
        });

        verify(profileChangeService).listAdminChangeRequests(1, 10, "PENDING", null);
    }
}
