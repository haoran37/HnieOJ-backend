package com.hnieacm.discussion.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.constant.AuthCacheConstant;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.result.Result;
import com.hnieacm.discussion.feign.AuthInternalFeignClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 讨论服务角色缓存回归：ADMIN/ROOT 从认证缓存命中；普通学生角色不会被提升；
 * 缓存缺失且内部刷新失败时按最小权限回退为 STUDENT（fail-closed）。
 */
class StpInterfaceImplTest {

    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private AuthInternalFeignClient authInternalFeignClient;
    private StpInterfaceImpl stpInterface;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        authInternalFeignClient = mock(AuthInternalFeignClient.class);
        stpInterface = new StpInterfaceImpl(stringRedisTemplate, new ObjectMapper(), authInternalFeignClient);
    }

    private static String rolesKey(String uid) {
        return AuthCacheConstant.ROLE_CACHE_PREFIX + uid;
    }

    @Test
    void adminRoleIsRecognizedFromCache() {
        when(valueOperations.get(rolesKey("1001"))).thenReturn("[\"admin\"]");

        List<String> roles = stpInterface.getRoleList("1001", "login");

        assertThat(roles).containsExactly(RoleConstant.ADMIN);
        verify(authInternalFeignClient, never()).refreshUserAuthCache(any());
    }

    @Test
    void rootRoleIsRecognizedFromCache() {
        when(valueOperations.get(rolesKey("1002"))).thenReturn("[\"root\"]");

        List<String> roles = stpInterface.getRoleList(1002L, "login");

        assertThat(roles).containsExactly(RoleConstant.ROOT);
        verify(authInternalFeignClient, never()).refreshUserAuthCache(any());
    }

    @Test
    void normalStudentIsNotElevated() {
        when(valueOperations.get(rolesKey("1003"))).thenReturn("[\"student\"]");

        List<String> roles = stpInterface.getRoleList("1003", "login");

        assertThat(roles).containsExactly(RoleConstant.STUDENT);
        assertThat(roles).doesNotContain(RoleConstant.ADMIN, RoleConstant.ROOT);
    }

    @Test
    void cacheMissAndRefreshFailureFallsBackToStudent() {
        when(valueOperations.get(rolesKey("1004"))).thenReturn(null);
        when(authInternalFeignClient.refreshUserAuthCache("1004"))
                .thenReturn(Result.error("refresh failed"));

        List<String> roles = stpInterface.getRoleList("1004", "login");

        assertThat(roles).containsExactly(RoleConstant.STUDENT);
        assertThat(roles).doesNotContain(RoleConstant.ADMIN, RoleConstant.ROOT);
        verify(authInternalFeignClient).refreshUserAuthCache("1004");
    }

    @Test
    void cacheMissTriggersRefreshAndReadsRefreshedRole() {
        AtomicInteger reads = new AtomicInteger();
        when(valueOperations.get(rolesKey("1005")))
                .thenAnswer(invocation -> reads.getAndIncrement() == 0 ? null : "[\"admin\"]");
        when(authInternalFeignClient.refreshUserAuthCache("1005")).thenReturn(Result.success(null));

        List<String> roles = stpInterface.getRoleList("1005", "login");

        assertThat(roles).containsExactly(RoleConstant.ADMIN);
        verify(authInternalFeignClient).refreshUserAuthCache("1005");
    }
}
