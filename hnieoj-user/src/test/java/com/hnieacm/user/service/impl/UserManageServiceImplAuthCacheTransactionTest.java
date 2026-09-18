package com.hnieacm.user.service.impl;

import com.hnieacm.auth.service.UserAuthCacheService;
import com.hnieacm.user.dto.GrantPermissionRequest;
import com.hnieacm.user.entity.UserInfo;
import com.hnieacm.user.entity.UserRole;
import com.hnieacm.user.mapper.RoleMapper;
import com.hnieacm.user.mapper.SysClassMapper;
import com.hnieacm.user.mapper.SysCollegeMapper;
import com.hnieacm.user.mapper.UserInfoMapper;
import com.hnieacm.user.mapper.UserRoleMapper;
import com.hnieacm.user.properties.UserManageProperties;
import com.hnieacm.user.service.manager.UserInfoManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date 2026/09/18
 * @Description: 权限缓存提交时序回归测试（merge-02 R1）。
 * <p>
 * 验证 {@code refreshAuthCacheSafely} 只在事务成功提交后刷新鉴权缓存：提交后刷新一次、
 * 回滚不刷新、无事务路径立即刷新。使用真实事务同步机制（{@link AbstractPlatformTransactionManager}），
 * 而非仅断言调用参数。
 */
@ExtendWith(MockitoExtension.class)
class UserManageServiceImplAuthCacheTransactionTest {

    @Mock
    private UserInfoMapper userInfoMapper;

    @Mock
    private SysCollegeMapper sysCollegeMapper;

    @Mock
    private SysClassMapper sysClassMapper;

    @Mock
    private UserRoleMapper userRoleMapper;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private UserAuthCacheService userAuthCacheService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private UserManageServiceImpl userManageService;

    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        UserManageProperties properties = new UserManageProperties();
        UserInfoManager userInfoManager = new UserInfoManager(userInfoMapper, sysCollegeMapper, sysClassMapper);
        userManageService = new UserManageServiceImpl(
                userInfoMapper,
                sysCollegeMapper,
                sysClassMapper,
                userRoleMapper,
                roleMapper,
                userAuthCacheService,
                stringRedisTemplate,
                properties,
                userInfoManager
        );
        transactionManager = new StubTransactionManager();
    }

    @Test
    void commitRefreshesAuthCacheOnlyAfterTransactionCompletes() {
        UserInfo user = user("u1");
        when(userInfoMapper.selectList(any())).thenReturn(List.of(user));
        when(userRoleMapper.selectList(any())).thenReturn(List.of());
        when(userRoleMapper.insert(any(UserRole.class))).thenReturn(1);

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            userManageService.grantPermissions(grantRequest("u1"));
            // 事务尚未提交，缓存不得被刷新（否则回滚会暴露未生效权限）。
            verify(userAuthCacheService, never()).cacheUserAuth(any());
        });

        verify(userAuthCacheService, times(1)).cacheUserAuth("u1");
    }

    @Test
    void rollbackDoesNotRefreshAuthCache() {
        UserInfo user = user("u2");
        when(userInfoMapper.selectList(any())).thenReturn(List.of(user));
        when(userRoleMapper.selectList(any())).thenReturn(List.of());
        when(userRoleMapper.insert(any(UserRole.class))).thenReturn(1);

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> template.executeWithoutResult(status -> {
            userManageService.grantPermissions(grantRequest("u2"));
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);

        verify(userAuthCacheService, never()).cacheUserAuth(any());
    }

    @Test
    void nonTransactionalCallRefreshesImmediately() {
        UserInfo user = user("u3");
        when(userInfoMapper.selectList(any())).thenReturn(List.of(user));
        when(userRoleMapper.selectList(any())).thenReturn(List.of());
        when(userRoleMapper.insert(any(UserRole.class))).thenReturn(1);

        userManageService.grantPermissions(grantRequest("u3"));

        verify(userAuthCacheService, times(1)).cacheUserAuth("u3");
    }

    private UserInfo user(String uid) {
        UserInfo user = new UserInfo();
        user.setUid(uid);
        user.setStatus(1);
        return user;
    }

    private GrantPermissionRequest grantRequest(String uid) {
        GrantPermissionRequest request = new GrantPermissionRequest();
        request.setUids(List.of(uid));
        request.setRole("admin");
        return request;
    }

    /**
     * 测试事务管理器：commit 触发 afterCommit/afterCompletion，rollback 仅触发 afterCompletion，
     * 与真实 {@link PlatformTransactionManager} 的同步语义一致，足以驱动事务同步回调。
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
            // 提交本身不抛异常即视为成功。
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            // 回滚本身不抛异常。
        }
    }
}
