package com.hnieacm.gateway.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.result.ResultCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Author: HaoRan_Lyu
 * @Date 2026/09/19
 * @Description: 网关鉴权配置回归测试：覆盖公共路径放行清单与错误响应映射，
 * 保证拆分超长方法后权限/错误行为不变。
 */
class SaTokenConfigTest {

    private final SaTokenConfig saTokenConfig = new SaTokenConfig();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsLoginFailureToUnauthorized() throws Exception {
        JsonNode body = errorBody(new NotLoginException("token expired", "login", NotLoginException.TOKEN_TIMEOUT));

        assertThat(body.get("code").asInt()).isEqualTo(ResultCode.UNAUTHORIZED);
        assertThat(body.get("msg").asText()).isEqualTo("未登录或登录已过期");
    }

    @Test
    void mapsRoleFailureToForbidden() throws Exception {
        JsonNode body = errorBody(new NotRoleException("admin"));

        assertThat(body.get("code").asInt()).isEqualTo(ResultCode.FORBIDDEN);
        assertThat(body.get("msg").asText()).isEqualTo("缺少角色权限");
    }

    @Test
    void mapsPermissionFailureToForbidden() throws Exception {
        JsonNode body = errorBody(new NotPermissionException("user:manage"));

        assertThat(body.get("code").asInt()).isEqualTo(ResultCode.FORBIDDEN);
        assertThat(body.get("msg").asText()).isEqualTo("缺少操作权限");
    }

    @Test
    void mapsSaTokenExceptionMessageToUnauthorized() throws Exception {
        JsonNode body = errorBody(new SaTokenException("custom auth failure"));

        assertThat(body.get("code").asInt()).isEqualTo(ResultCode.UNAUTHORIZED);
        assertThat(body.get("msg").asText()).isEqualTo("custom auth failure");
    }

    @Test
    void mapsAuthCacheFailureToInternalError() throws Exception {
        JsonNode body = errorBody(new IllegalStateException("cache unavailable"));

        assertThat(body.get("code").asInt()).isEqualTo(ResultCode.INTERNAL_ERROR);
        assertThat(body.get("msg").asText()).isEqualTo("权限数据加载失败，请稍后重试");
    }

    @Test
    void mapsUnknownFailureToUnauthorized() throws Exception {
        JsonNode body = errorBody(new RuntimeException("boom"));

        assertThat(body.get("code").asInt()).isEqualTo(ResultCode.UNAUTHORIZED);
        assertThat(body.get("msg").asText()).isEqualTo("认证失败");
    }

    @Test
    void filterKeepsPublicPathsExcludedAndAllPathsIncluded() {
        SaReactorFilter filter = saTokenConfig.saReactorFilter(objectMapper);

        assertThat(filter.includeList).containsExactly("/**");
        assertThat(filter.excludeList).contains(
                "/api/auth/login",
                "/api/auth/register",
                "/api/judge/temp-token",
                "/judge/problems/**",
                "/judge/submissions/**",
                "/judge/nodes/**",
                "/ws/submissions/**"
        );
    }

    private JsonNode errorBody(Throwable ex) throws Exception {
        String json = (String) saTokenConfig.buildAuthErrorResponse(objectMapper, ex);
        return objectMapper.readTree(json);
    }
}
