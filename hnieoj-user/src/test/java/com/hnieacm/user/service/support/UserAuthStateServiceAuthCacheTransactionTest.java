package com.hnieacm.user.service.support;

import com.hnieacm.auth.service.UserAuthCacheService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @Author: HaoRan_Lyu
 * @Date 2026/09/19
 * @Description: 权限缓存提交时序回归测试。
 * <p>
 * 合并认证域后，缓存刷新职责落在 {@link UserAuthStateService}：授权/改权事务提交后才刷新、
 * 回滚不刷新、无事务路径立即刷新；同时验证刷新走本地 {@link UserAuthCacheService}，
 * 不再经过已删除的本地 auth 自调用 Feign。测试使用真实事务同步机制
 * （{@link AbstractPlatformTransactionManager}），而非仅断言调用参数。
 */
@ExtendWith(MockitoExtension.class)
class UserAuthStateServiceAuthCacheTransactionTest {

    @Mock
    private UserAuthCacheService userAuthCacheService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private UserAuthStateService userAuthStateService;

    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        userAuthStateService = new UserAuthStateService(userAuthCacheService, stringRedisTemplate);
        transactionManager = new StubTransactionManager();
    }

    @Test
    void commitRefreshesAuthCacheOnlyAfterTransactionCompletes() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            userAuthStateService.refreshAuthCacheSafely("u1");
            // 事务尚未提交，缓存不得被刷新（否则回滚会暴露未生效权限）。
            verify(userAuthCacheService, never()).cacheUserAuth(anyString());
        });

        verify(userAuthCacheService, times(1)).cacheUserAuth("u1");
    }

    @Test
    void rollbackDoesNotRefreshAuthCache() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> template.executeWithoutResult(status -> {
            userAuthStateService.refreshAuthCacheSafely("u2");
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);

        verify(userAuthCacheService, never()).cacheUserAuth(anyString());
    }

    @Test
    void nonTransactionalCallRefreshesImmediately() {
        userAuthStateService.refreshAuthCacheSafely("u3");

        verify(userAuthCacheService, times(1)).cacheUserAuth("u3");
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
