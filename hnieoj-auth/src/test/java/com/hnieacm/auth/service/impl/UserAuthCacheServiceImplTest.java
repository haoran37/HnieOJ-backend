package com.hnieacm.auth.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.auth.properties.AuthCacheTtlProperties;
import com.hnieacm.auth.service.AuthPermissionService;
import com.hnieacm.common.constant.AuthCacheConstant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/08
 * @Description: User auth cache TTL tests.
 */
@ExtendWith(MockitoExtension.class)
class UserAuthCacheServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private AuthPermissionService authPermissionService;

    @Test
    void shouldAddJitterWhenWritingAuthCacheTtl() {
        AuthCacheTtlProperties properties = new AuthCacheTtlProperties();
        properties.setRoles(Duration.ofHours(1));
        properties.setPermissions(Duration.ofHours(2));
        properties.setJitter(Duration.ofSeconds(30));
        UserAuthCacheServiceImpl service = new UserAuthCacheServiceImpl(
                stringRedisTemplate,
                new ObjectMapper(),
                authPermissionService,
                properties
        );
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(authPermissionService.getUserRoles("u1")).thenReturn(List.of("admin"));
        when(authPermissionService.getUserPermissions("u1")).thenReturn(List.of("problem:update"));

        service.cacheUserAuth("u1");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations, times(2)).set(keyCaptor.capture(), anyString(), ttlCaptor.capture());
        Map<String, Duration> ttlByKey = IntStream.range(0, keyCaptor.getAllValues().size())
                .boxed()
                .collect(java.util.stream.Collectors.toMap(
                        keyCaptor.getAllValues()::get,
                        ttlCaptor.getAllValues()::get
                ));

        assertThat(ttlByKey.get(AuthCacheConstant.ROLE_CACHE_PREFIX + "u1"))
                .isBetween(Duration.ofHours(1), Duration.ofHours(1).plusSeconds(30));
        assertThat(ttlByKey.get(AuthCacheConstant.PERMISSION_CACHE_PREFIX + "u1"))
                .isBetween(Duration.ofHours(2), Duration.ofHours(2).plusSeconds(30));
    }
}
