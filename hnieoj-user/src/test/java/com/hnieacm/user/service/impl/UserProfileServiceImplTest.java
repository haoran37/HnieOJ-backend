package com.hnieacm.user.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.hnieacm.common.constant.AuthCacheConstant;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.user.dto.ChangeCurrentPasswordRequest;
import com.hnieacm.user.entity.UserInfo;
import com.hnieacm.user.mapper.RoleMapper;
import com.hnieacm.user.mapper.UserInfoMapper;
import com.hnieacm.user.mapper.UserRoleMapper;
import com.hnieacm.user.properties.UserManageProperties;
import com.hnieacm.user.service.manager.UserInfoManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/08
 * @Description: Current user password change tests.
 */
@ExtendWith(MockitoExtension.class)
class UserProfileServiceImplTest {

    @Mock
    private UserInfoManager userInfoManager;

    @Mock
    private UserInfoMapper userInfoMapper;

    @Mock
    private UserRoleMapper userRoleMapper;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private UserManageProperties userManageProperties;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private UserProfileServiceImpl userProfileService;

    @BeforeEach
    void setUp() {
        userProfileService = new UserProfileServiceImpl(
                userInfoManager,
                userInfoMapper,
                userRoleMapper,
                roleMapper,
                userManageProperties,
                stringRedisTemplate
        );
        when(userManageProperties.getPasswordMinLength()).thenReturn(6);
        when(userManageProperties.getPasswordMaxLength()).thenReturn(32);
    }

    @Test
    void shouldRejectPasswordChangeWhenOldPasswordIsWrong() {
        UserInfo user = new UserInfo();
        user.setUid("u1");
        user.setPassword(BCrypt.hashpw("Old@123456"));
        when(userInfoManager.getUserByUid("u1")).thenReturn(user);

        ChangeCurrentPasswordRequest request = new ChangeCurrentPasswordRequest();
        request.setOldPassword("Wrong@123456");
        request.setPassword("New@123456");

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsString).thenReturn("u1");

            assertThatThrownBy(() -> userProfileService.changeCurrentPassword(request))
                    .isInstanceOf(BizException.class);
        }

        verify(userInfoMapper, never()).updateById(user);
    }

    @Test
    void shouldUpdatePasswordAndClearResetFlagWhenOldPasswordMatches() {
        UserInfo user = new UserInfo();
        user.setUid("u1");
        user.setPassword(BCrypt.hashpw("Old@123456"));
        user.setPasswordResetRequired(true);
        when(userInfoManager.getUserByUid("u1")).thenReturn(user);

        ChangeCurrentPasswordRequest request = new ChangeCurrentPasswordRequest();
        request.setOldPassword("Old@123456");
        request.setPassword("New@123456");

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsString).thenReturn("u1");
            stpUtil.when(() -> StpUtil.getTokenValueListByLoginId("u1")).thenReturn(List.of());

            userProfileService.changeCurrentPassword(request);
        }

        assertThat(BCrypt.checkpw("New@123456", user.getPassword())).isTrue();
        assertThat(user.getPasswordResetRequired()).isFalse();
        verify(userInfoMapper).updateById(user);
        verify(stringRedisTemplate).delete(AuthCacheConstant.ROLE_CACHE_PREFIX + "u1");
        verify(stringRedisTemplate).delete(AuthCacheConstant.PERMISSION_CACHE_PREFIX + "u1");
    }
}
