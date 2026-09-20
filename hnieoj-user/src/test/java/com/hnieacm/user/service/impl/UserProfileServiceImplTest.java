package com.hnieacm.user.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.user.dto.UpdatePasswordRequest;
import com.hnieacm.user.dto.UpdateUserProfileRequest;
import com.hnieacm.user.entity.UserInfo;
import com.hnieacm.user.mapper.RoleMapper;
import com.hnieacm.user.mapper.UserInfoMapper;
import com.hnieacm.user.mapper.UserRoleMapper;
import com.hnieacm.user.properties.UserManageProperties;
import com.hnieacm.user.service.manager.UserInfoManager;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 自助资料/密码服务回归：字段白名单、null 保留、空串清除、URL/QQ 校验、
 * 旧密码错误不写库不踢下线、密码提交后才失效会话。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserProfileServiceImplTest {

    @Mock
    private UserInfoManager userInfoManager;

    @Mock
    private UserRoleMapper userRoleMapper;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private UserInfoMapper userInfoMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private UserProfileServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MyBatisPlusTestSupport.initTableInfo(UserInfo.class);
    }

    @BeforeEach
    void setUp() {
        service = new UserProfileServiceImpl(
                userInfoManager,
                userRoleMapper,
                roleMapper,
                userInfoMapper,
                new UserManageProperties(),
                stringRedisTemplate
        );
    }

    @Test
    void requestDtoOnlyExposesWhitelistFields() {
        Set<String> fields = Arrays.stream(UpdateUserProfileRequest.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertThat(fields).containsExactlyInAnyOrder("username", "avatar", "qq", "github", "blog");
    }

    @Test
    void passwordRequestToStringExcludesSecrets() {
        UpdatePasswordRequest request = new UpdatePasswordRequest();
        request.setOldPassword("super-secret-old");
        request.setNewPassword("super-secret-new");

        assertThat(request.toString())
                .doesNotContain("super-secret-old")
                .doesNotContain("super-secret-new");
    }

    @Test
    void updateProfilePreservesNullFieldsAndClearsEmptyOptional() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setUsername("newname");
        request.setAvatar("");   // 空串清除
        // qq/github/blog 为 null 表示未传，必须不出现在 UPDATE 的 SET 子句里，保留旧值。
        service.updateCurrentUserProfile("u1", request);

        LambdaUpdateWrapper<UserInfo> wrapper = captureUpdateWrapper();
        String setSql = wrapper.getSqlSet();
        assertThat(setSql).contains("username=").contains("avatar=");
        assertThat(setSql)
                .doesNotContain("qq")
                .doesNotContain("github")
                .doesNotContain("blog");
        // 空串清除：绑定参数为 null，真正生成 SET avatar = NULL，而不是被 NOT_NULL 策略忽略。
        assertThat(wrapper.getParamNameValuePairs()).containsValue(null);
        // WHERE 使用服务端 uid。
        assertThat(wrapper.getTargetSql()).contains("uid");
    }

    @Test
    void updateProfileWritesOnlyWhitelistColumnsWithoutSecurityFields() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setGithub("");   // 仅清除 github，其余未传
        service.updateCurrentUserProfile("u1", request);

        LambdaUpdateWrapper<UserInfo> wrapper = captureUpdateWrapper();
        String setSql = wrapper.getSqlSet();
        assertThat(setSql).contains("github=");
        // 不得回写密码/身份/角色关联等整行旧快照字段。
        assertThat(setSql)
                .doesNotContain("password")
                .doesNotContain("email")
                .doesNotContain("phone")
                .doesNotContain("college_id")
                .doesNotContain("class_id")
                .doesNotContain("grade")
                .doesNotContain("realname")
                .doesNotContain("cf_username")
                .doesNotContain("status")
                .doesNotContain("ip_restricted")
                .doesNotContain("ip_whitelist")
                .doesNotContain("username")
                .doesNotContain("avatar")
                .doesNotContain("qq")
                .doesNotContain("blog");
        assertThat(wrapper.getParamNameValuePairs()).containsValue(null);
    }

    @Test
    void updateProfileDoesNotWriteWhenAllFieldsNull() {
        service.updateCurrentUserProfile("u1", new UpdateUserProfileRequest());
        verify(userInfoMapper, never()).update(any(), any());
    }

    @Test
    void updateProfileRejectsBlankUid() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setUsername("newname");

        assertThatThrownBy(() -> service.updateCurrentUserProfile("  ", request))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.UNAUTHORIZED);
        verify(userInfoMapper, never()).update(any(), any());
    }

    @Test
    void updateProfileAcceptsInternalAvatarPath() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setAvatar("/oj/images/1/avatar.png");
        service.updateCurrentUserProfile("u1", request);

        LambdaUpdateWrapper<UserInfo> wrapper = captureUpdateWrapper();
        assertThat(wrapper.getSqlSet()).contains("avatar=");
        assertThat(wrapper.getParamNameValuePairs()).containsValue("/oj/images/1/avatar.png");
    }

    @Test
    void updateProfileRejectsJavascriptScheme() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setAvatar("javascript:alert(1)");

        assertThatThrownBy(() -> service.updateCurrentUserProfile("u1", request))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST);
        verify(userInfoMapper, never()).update(any(), any());
    }

    @Test
    void updateProfileRejectsBlankUsernameAndBadQqAndNonHttpUrl() {
        UpdateUserProfileRequest blankUsername = new UpdateUserProfileRequest();
        blankUsername.setUsername("   ");
        assertThatThrownBy(() -> service.updateCurrentUserProfile("u1", blankUsername))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("username");

        UpdateUserProfileRequest badQq = new UpdateUserProfileRequest();
        badQq.setQq("abc");
        assertThatThrownBy(() -> service.updateCurrentUserProfile("u1", badQq))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.INVALID_QQ);

        UpdateUserProfileRequest badGithub = new UpdateUserProfileRequest();
        badGithub.setGithub("ftp://github.com/a");
        assertThatThrownBy(() -> service.updateCurrentUserProfile("u1", badGithub))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST);

        UpdateUserProfileRequest badUsername = new UpdateUserProfileRequest();
        badUsername.setUsername("x");
        assertThatThrownBy(() -> service.updateCurrentUserProfile("u1", badUsername))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.INVALID_USERNAME);

        verify(userInfoMapper, never()).update(any(), any());
    }

    @Test
    void updateProfileAcceptanceLengthBoundaries() {
        // avatar 500 允许，501 拒绝；github/blog 255 允许，256 拒绝（DB 列长，避免 DataTooLong -> 500）。
        String avatar500 = "https://cdn.example.test/" + "a".repeat(500 - "https://cdn.example.test/".length());
        assertThat(avatar500).hasSize(500);
        UpdateUserProfileRequest okAvatar = new UpdateUserProfileRequest();
        okAvatar.setAvatar(avatar500);
        service.updateCurrentUserProfile("u1", okAvatar);
        LambdaUpdateWrapper<UserInfo> avatarWrapper = captureUpdateWrapper();
        assertThat(avatarWrapper.getParamNameValuePairs()).containsValue(avatar500);

        UpdateUserProfileRequest tooLongAvatar = new UpdateUserProfileRequest();
        tooLongAvatar.setAvatar(avatar500 + "a");
        assertThatThrownBy(() -> service.updateCurrentUserProfile("u1", tooLongAvatar))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST);

        String url255 = "https://example.test/" + "b".repeat(255 - "https://example.test/".length());
        assertThat(url255).hasSize(255);
        UpdateUserProfileRequest okGithub = new UpdateUserProfileRequest();
        okGithub.setGithub(url255);
        service.updateCurrentUserProfile("u1", okGithub);

        UpdateUserProfileRequest tooLongGithub = new UpdateUserProfileRequest();
        tooLongGithub.setGithub(url255 + "b");
        assertThatThrownBy(() -> service.updateCurrentUserProfile("u1", tooLongGithub))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("github")
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST);

        UpdateUserProfileRequest okBlog = new UpdateUserProfileRequest();
        okBlog.setBlog(url255);
        service.updateCurrentUserProfile("u1", okBlog);

        UpdateUserProfileRequest tooLongBlog = new UpdateUserProfileRequest();
        tooLongBlog.setBlog(url255 + "b");
        assertThatThrownBy(() -> service.updateCurrentUserProfile("u1", tooLongBlog))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("blog")
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST);
    }

    @Test
    void updateProfileAcceptsHttpAndHttpsAndRejectsForbiddenSchemes() {
        UpdateUserProfileRequest http = new UpdateUserProfileRequest();
        http.setBlog("http://blog.example.test");
        service.updateCurrentUserProfile("u1", http);

        UpdateUserProfileRequest https = new UpdateUserProfileRequest();
        https.setBlog("https://blog.example.test");
        service.updateCurrentUserProfile("u1", https);

        UpdateUserProfileRequest data = new UpdateUserProfileRequest();
        data.setAvatar("data:image/png;base64,AAAA");
        assertThatThrownBy(() -> service.updateCurrentUserProfile("u1", data))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST);

        UpdateUserProfileRequest protocolRelative = new UpdateUserProfileRequest();
        protocolRelative.setAvatar("//evil.example.test/a.png");
        assertThatThrownBy(() -> service.updateCurrentUserProfile("u1", protocolRelative))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST);
    }

    @SuppressWarnings("unchecked")
    private LambdaUpdateWrapper<UserInfo> captureUpdateWrapper() {
        ArgumentCaptor<LambdaUpdateWrapper<UserInfo>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(userInfoMapper).update(isNull(), captor.capture());
        return captor.getValue();
    }

    @Test
    void wrongOldPasswordDoesNotWriteOrInvalidateSessions() {
        UserProfileServiceImpl spyService = spy(service);
        doNothing().when(spyService).invalidateSessions(anyString());

        UserInfo user = user("u1");
        user.setPassword(BCrypt.hashpw("oldpass"));
        when(userInfoMapper.selectOne(any())).thenReturn(user);

        UpdatePasswordRequest request = new UpdatePasswordRequest();
        request.setOldPassword("wrongpass");
        request.setNewPassword("newpass123");

        assertThatThrownBy(() -> spyService.updatePassword("u1", request))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.PASSWORD_ERROR);

        verify(userInfoMapper, never()).updateById(any(UserInfo.class));
        verify(spyService, never()).invalidateSessions(anyString());
        verify(stringRedisTemplate, never()).delete(anyString());
    }

    @Test
    void wrongOldPasswordDoesNotChangeStoredHash() {
        UserInfo user = user("u1");
        String originalHash = BCrypt.hashpw("oldpass");
        user.setPassword(originalHash);
        when(userInfoMapper.selectOne(any())).thenReturn(user);

        UpdatePasswordRequest request = new UpdatePasswordRequest();
        request.setOldPassword("nope");
        request.setNewPassword("newpass123");

        assertThatThrownBy(() -> service.updatePassword("u1", request))
                .isInstanceOf(BizException.class);
        assertThat(user.getPassword()).isEqualTo(originalHash);
    }

    @Test
    void newPasswordLengthIsValidated() {
        UserInfo user = user("u1");
        user.setPassword(BCrypt.hashpw("oldpass"));
        when(userInfoMapper.selectOne(any())).thenReturn(user);

        UpdatePasswordRequest request = new UpdatePasswordRequest();
        request.setOldPassword("oldpass");
        request.setNewPassword("123");

        assertThatThrownBy(() -> service.updatePassword("u1", request))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST);
        verify(userInfoMapper, never()).updateById(any(UserInfo.class));
    }

    @Test
    void sessionsInvalidatedOnlyAfterCommit() {
        UserProfileServiceImpl spyService = spy(service);
        doNothing().when(spyService).invalidateSessions(anyString());

        UserInfo user = user("u1");
        user.setPassword(BCrypt.hashpw("oldpass"));
        when(userInfoMapper.selectOne(any())).thenReturn(user);

        UpdatePasswordRequest request = new UpdatePasswordRequest();
        request.setOldPassword("oldpass");
        request.setNewPassword("newpass123");

        TransactionTemplate template = new TransactionTemplate(new StubTransactionManager());
        template.executeWithoutResult(status -> {
            spyService.updatePassword("u1", request);
            // 事务提交前不得踢下线。
            verify(spyService, never()).invalidateSessions(anyString());
        });

        verify(spyService, times(1)).invalidateSessions("u1");
        verify(userInfoMapper).updateById(user);
        assertThat(BCrypt.checkpw("newpass123", user.getPassword())).isTrue();
    }

    private UserInfo user(String uid) {
        UserInfo user = new UserInfo();
        user.setUuid("uuid-" + uid);
        user.setUid(uid);
        user.setStatus(0);
        return user;
    }

    /**
     * 测试事务管理器：commit 触发 afterCommit，rollback 仅触发 afterCompletion。
     */
    private static final class StubTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            assertThat(transaction).isNotNull();
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
