package com.hnieacm.gateway.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.spring.pathmatch.SaPathPatternParserUtil;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.strategy.SaStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.constant.PermissionConstant;
import com.hnieacm.common.constant.RoleConstant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Author: HaoRan_Lyu
 * @Description: 网关鉴权路由回归测试：只放行注册所需的 3 类 GET 精确路径，其余仍在登录后访问。
 * <p>
 * 直接驱动 {@link SaReactorFilter}，使用 Sa-Token 与生产一致的 PathPattern 路由匹配器。
 */
class SaTokenConfigRouteRuleTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static SaReactorFilter filter;

    @BeforeAll
    static void setUp() {
        // 生产环境由 SaTokenContextRegister 注入；离线单测按相同实现手动装配。
        SaStrategy.instance.routeMatcher = SaPathPatternParserUtil::match;
        SaManager.setStpInterface(new StpInterface() {
            @Override
            public List<String> getPermissionList(Object loginId, String loginType) {
                return switch (String.valueOf(loginId)) {
                    // root 具备全部题目管理权限；admin 仅具备用户管理权限，用于验证低权限管理员
                    case "root" -> List.of(
                            PermissionConstant.USER_MANAGE,
                            PermissionConstant.PROBLEM_CREATE,
                            PermissionConstant.PROBLEM_UPDATE,
                            PermissionConstant.PROBLEM_DELETE
                    );
                    case "admin" -> List.of(PermissionConstant.USER_MANAGE);
                    default -> List.of();
                };
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
        filter = new SaTokenConfig().saReactorFilter(MAPPER);
    }

    @Test
    void registrationBaseDataGetPathsReachDownstreamWithoutLogin() {
        assertThat(invoke(HttpMethod.GET, "/api/colleges").downstream()).isTrue();
        assertThat(invoke(HttpMethod.GET, "/api/colleges/1/grades").downstream()).isTrue();
        assertThat(invoke(HttpMethod.GET, "/api/colleges/1/grades/2026/classes").downstream()).isTrue();
    }

    @Test
    void teacherAndTaQueriesStillRequireLogin() {
        Invocation teachers = invoke(HttpMethod.GET, "/api/classes/1/teachers");
        Invocation tas = invoke(HttpMethod.GET, "/api/classes/1/tas");

        assertThat(teachers.downstream()).isFalse();
        assertThat(tas.downstream()).isFalse();
        assertThat(teachers.body()).contains("401");
        assertThat(tas.body()).contains("401");
    }

    @Test
    void writeMethodsAndNonExactCollegeSubPathsAreNotPublic() {
        assertThat(invoke(HttpMethod.POST, "/api/colleges").downstream()).isFalse();
        assertThat(invoke(HttpMethod.PUT, "/api/colleges/1/grades").downstream()).isFalse();
        assertThat(invoke(HttpMethod.DELETE, "/api/colleges/1/grades/2026/classes").downstream()).isFalse();
        assertThat(invoke(HttpMethod.GET, "/api/colleges/1").downstream()).isFalse();
        assertThat(invoke(HttpMethod.GET, "/api/colleges/1/grades/2026").downstream()).isFalse();
        assertThat(invoke(HttpMethod.GET, "/api/colleges/1/grades/2026/classes/5").downstream()).isFalse();
        assertThat(invoke(HttpMethod.GET, "/api/colleges/1/grades/2026/classes/5/teachers").downstream()).isFalse();
    }

    @Test
    void userImportTemplateStillRequiresLoginAtGateway() {
        Invocation invocation = invoke(HttpMethod.GET, "/api/users/import/template");
        assertThat(invocation.downstream()).isFalse();
        assertThat(invocation.body()).contains("401");
    }

    @Test
    void authErrorsAreWrittenAsJsonWithAccurateBusinessCode() {
        // 401：匿名访问用户导入模板下载接口
        Invocation anonymous = invoke(HttpMethod.GET, "/api/users/import/template");
        assertThat(anonymous.downstream()).isFalse();
        assertThat(anonymous.contentType()).isNotNull();
        assertThat(anonymous.contentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        assertThat(businessCode(anonymous.body())).isEqualTo(401);

        // 403：已登录学生缺少 user:manage 权限
        Invocation forbidden = invokeWithToken(HttpMethod.GET, "/api/users/import/template", tokenFor("student"));
        assertThat(forbidden.downstream()).isFalse();
        assertThat(forbidden.contentType()).isNotNull();
        assertThat(forbidden.contentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        assertThat(businessCode(forbidden.body())).isEqualTo(403);

        // 403：学生访问成就附件下载接口
        Invocation achievement = invokeWithToken(HttpMethod.GET, "/api/admin/achievements/8/file", tokenFor("student"));
        assertThat(achievement.downstream()).isFalse();
        assertThat(achievement.contentType()).isNotNull();
        assertThat(achievement.contentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        assertThat(businessCode(achievement.body())).isEqualTo(403);
    }

    @Test
    void successfulAdminFileDownloadIsNotRewrittenToJsonByGateway() {
        Invocation admin = invokeWithToken(HttpMethod.GET, "/api/admin/achievements/8/file", tokenFor("admin"));
        assertThat(admin.downstream()).isTrue();
        // 成功文件流由下游 Controller 决定，网关错误回调不得注入 JSON 媒体类型
        assertThat(admin.contentType()).isNotEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void userImportTemplateRequiresUserManagePermission() {
        Invocation student = invokeWithToken(HttpMethod.GET, "/api/users/import/template", tokenFor("student"));
        assertThat(student.downstream()).isFalse();
        assertThat(student.body()).contains("403");

        Invocation admin = invokeWithToken(HttpMethod.GET, "/api/users/import/template", tokenFor("admin"));
        assertThat(admin.downstream()).isTrue();
    }

    @Test
    void registrationReviewIsAnonymous401AndStudent403() {
        Invocation anonymous = invoke(HttpMethod.GET, "/api/registrations?page=1&pageSize=10");
        assertThat(anonymous.downstream()).isFalse();
        assertThat(anonymous.body()).contains("401");

        assertThat(invoke(HttpMethod.POST, "/api/registrations/u1/approve").body()).contains("401");
        assertThat(invoke(HttpMethod.POST, "/api/registrations/u1/reject").body()).contains("401");
        assertThat(invoke(HttpMethod.POST, "/api/registrations/batch/approve").body()).contains("401");

        String student = tokenFor("student");
        Invocation list = invokeWithToken(HttpMethod.GET, "/api/registrations?page=1&pageSize=10", student);
        assertThat(list.downstream()).isFalse();
        assertThat(list.body()).contains("403");

        Invocation approve = invokeWithToken(HttpMethod.POST, "/api/registrations/u1/approve", student);
        assertThat(approve.downstream()).isFalse();
        assertThat(approve.body()).contains("403");

        Invocation reject = invokeWithToken(HttpMethod.POST, "/api/registrations/u1/reject", student);
        assertThat(reject.downstream()).isFalse();
        assertThat(reject.body()).contains("403");

        Invocation batch = invokeWithToken(HttpMethod.POST, "/api/registrations/batch/approve", student);
        assertThat(batch.downstream()).isFalse();
        assertThat(batch.body()).contains("403");

        String admin = tokenFor("admin");
        String root = tokenFor("root");
        assertThat(invokeWithToken(HttpMethod.GET, "/api/registrations?page=1&pageSize=10", admin).downstream())
                .isTrue();
        assertThat(invokeWithToken(HttpMethod.POST, "/api/registrations/u1/approve", root).downstream()).isTrue();
        assertThat(invokeWithToken(HttpMethod.POST, "/api/registrations/u1/reject", admin).downstream()).isTrue();
        assertThat(invokeWithToken(HttpMethod.POST, "/api/registrations/batch/approve", admin).downstream()).isTrue();
    }

    @Test
    void adminProblemDetailGetRequiresProblemUpdatePermission() {
        // 匿名：401
        Invocation anonymous = invoke(HttpMethod.GET, "/api/admin/problem/1");
        assertThat(anonymous.downstream()).isFalse();
        assertThat(businessCode(anonymous.body())).isEqualTo(401);

        // 学生：缺 ADMIN/ROOT 角色，403
        Invocation student = invokeWithToken(HttpMethod.GET, "/api/admin/problem/1", tokenFor("student"));
        assertThat(student.downstream()).isFalse();
        assertThat(businessCode(student.body())).isEqualTo(403);

        // 低权限管理员：具备 ADMIN 角色但无 problem:update，403
        Invocation lowPermissionAdmin = invokeWithToken(HttpMethod.GET, "/api/admin/problem/1", tokenFor("admin"));
        assertThat(lowPermissionAdmin.downstream()).isFalse();
        assertThat(businessCode(lowPermissionAdmin.body())).isEqualTo(403);

        // 具备 problem:update 的管理员：放行到下游
        assertThat(invokeWithToken(HttpMethod.GET, "/api/admin/problem/1", tokenFor("root")).downstream()).isTrue();

        // 精准规则不覆盖列表接口：低权限管理员仍可访问 /api/admin/problem/list（行为不变）
        assertThat(invokeWithToken(HttpMethod.GET, "/api/admin/problem/list?page=1&pageSize=10", tokenFor("admin"))
                .downstream()).isTrue();
    }

    @Test
    void problemImagesAreAnonymousOnlyForGetTwoSegmentPaths() {
        assertThat(invoke(HttpMethod.GET, "/oj/images/8/one.png").downstream()).isTrue();

        // 更深子路径（如 testdata）不匿名，仍需登录
        assertThat(invoke(HttpMethod.GET, "/oj/images/8/testdata/1.in").downstream()).isFalse();
        assertThat(invoke(HttpMethod.GET, "/oj/images/8/sub/one.png").downstream()).isFalse();
        assertThat(invoke(HttpMethod.GET, "/oj/images/8").downstream()).isFalse();
        assertThat(invoke(HttpMethod.GET, "/oj/images").downstream()).isFalse();

        // 写方法不匿名
        assertThat(invoke(HttpMethod.POST, "/oj/images/8/one.png").downstream()).isFalse();
        assertThat(invoke(HttpMethod.PUT, "/oj/images/8/one.png").downstream()).isFalse();
        assertThat(invoke(HttpMethod.DELETE, "/oj/images/8/one.png").downstream()).isFalse();
    }

    @Test
    void tagCatalogRequiresLoginAndTagWritesRequireProblemPermissions() {
        // 匿名读标签：401
        Invocation anonymousRead = invoke(HttpMethod.GET, "/api/tags");
        assertThat(anonymousRead.downstream()).isFalse();
        assertThat(businessCode(anonymousRead.body())).isEqualTo(401);

        // 登录用户（含学生）可读标签：放行到下游
        assertThat(invokeWithToken(HttpMethod.GET, "/api/tags", tokenFor("student")).downstream()).isTrue();

        // 匿名管理写：401
        Invocation anonymousWrite = invoke(HttpMethod.POST, "/api/admin/tags");
        assertThat(anonymousWrite.downstream()).isFalse();
        assertThat(businessCode(anonymousWrite.body())).isEqualTo(401);

        // 学生缺少 ADMIN/ROOT 角色：403
        Invocation studentWrite = invokeWithToken(HttpMethod.POST, "/api/admin/tags", tokenFor("student"));
        assertThat(studentWrite.downstream()).isFalse();
        assertThat(businessCode(studentWrite.body())).isEqualTo(403);

        // 低权限管理员（有 ADMIN 角色但无 problem:create）：403
        Invocation lowPermissionCreate = invokeWithToken(HttpMethod.POST, "/api/admin/tags", tokenFor("admin"));
        assertThat(lowPermissionCreate.downstream()).isFalse();
        assertThat(businessCode(lowPermissionCreate.body())).isEqualTo(403);

        // root 具备 problem:create/update/delete：放行
        assertThat(invokeWithToken(HttpMethod.POST, "/api/admin/tags", tokenFor("root")).downstream()).isTrue();
        assertThat(invokeWithToken(HttpMethod.PUT, "/api/admin/tags/1", tokenFor("root")).downstream()).isTrue();
        assertThat(invokeWithToken(HttpMethod.DELETE, "/api/admin/tags/1", tokenFor("root")).downstream()).isTrue();

        // 低权限管理员更新/删除标签：403
        Invocation lowPermissionUpdate = invokeWithToken(HttpMethod.PUT, "/api/admin/tags/1", tokenFor("admin"));
        assertThat(lowPermissionUpdate.downstream()).isFalse();
        assertThat(businessCode(lowPermissionUpdate.body())).isEqualTo(403);
        Invocation lowPermissionDelete = invokeWithToken(HttpMethod.DELETE, "/api/admin/tags/1", tokenFor("admin"));
        assertThat(lowPermissionDelete.downstream()).isFalse();
        assertThat(businessCode(lowPermissionDelete.body())).isEqualTo(403);

        // 根路径 PUT（保存分组配置）：网关层只做 ADMIN/ROOT 角色校验（学生 403），
        // problem:update 细粒度校验由 hnieoj-problem 服务内 @SaCheckPermission 执行（不在网关断言）。
        Invocation studentSaveConfig = invokeWithToken(HttpMethod.PUT, "/api/admin/tags", tokenFor("student"));
        assertThat(studentSaveConfig.downstream()).isFalse();
        assertThat(businessCode(studentSaveConfig.body())).isEqualTo(403);
        assertThat(invokeWithToken(HttpMethod.PUT, "/api/admin/tags", tokenFor("admin")).downstream()).isTrue();
    }

    @Test
    void gatewayYamlRoutesTagPathsToProblemService() throws Exception {
        String pathLine = readRoutePathPredicate("hnieoj-problem");
        assertThat(pathLine).contains("/api/tags").contains("/api/admin/tags/**");
    }

    @Test
    void adminNoticeRoutesRequireAdminOrRootAtGateway() {
        // 匿名：401
        Invocation anonymous = invoke(HttpMethod.GET, "/api/admin/notices?page=1&pageSize=10");
        assertThat(anonymous.downstream()).isFalse();
        assertThat(businessCode(anonymous.body())).isEqualTo(401);

        // 学生缺少 ADMIN/ROOT：403
        Invocation student = invokeWithToken(HttpMethod.GET, "/api/admin/notices?page=1&pageSize=10",
                tokenFor("student"));
        assertThat(student.downstream()).isFalse();
        assertThat(businessCode(student.body())).isEqualTo(403);

        // admin/root 放行到下游；发布接口同样受 /api/admin/** 规则保护
        assertThat(invokeWithToken(HttpMethod.GET, "/api/admin/notices?page=1&pageSize=10", tokenFor("admin"))
                .downstream()).isTrue();
        assertThat(invokeWithToken(HttpMethod.POST, "/api/admin/notices/1/publish", tokenFor("student"))
                .downstream()).isFalse();
        assertThat(invokeWithToken(HttpMethod.POST, "/api/admin/notices/1/publish", tokenFor("root"))
                .downstream()).isTrue();
    }

    @Test
    void adminProfileChangeRoutesRequireAdminOrRootAtGateway() {
        Invocation anonymous = invoke(HttpMethod.GET, "/api/admin/profile-change-requests?page=1&pageSize=10");
        assertThat(anonymous.downstream()).isFalse();
        assertThat(businessCode(anonymous.body())).isEqualTo(401);

        Invocation student = invokeWithToken(HttpMethod.GET, "/api/admin/profile-change-requests?page=1&pageSize=10",
                tokenFor("student"));
        assertThat(student.downstream()).isFalse();
        assertThat(businessCode(student.body())).isEqualTo(403);

        Invocation studentApprove = invokeWithToken(HttpMethod.POST, "/api/admin/profile-change-requests/1/approve",
                tokenFor("student"));
        assertThat(studentApprove.downstream()).isFalse();
        assertThat(businessCode(studentApprove.body())).isEqualTo(403);

        assertThat(invokeWithToken(HttpMethod.GET, "/api/admin/profile-change-requests?page=1&pageSize=10",
                tokenFor("admin")).downstream()).isTrue();
        assertThat(invokeWithToken(HttpMethod.POST, "/api/admin/profile-change-requests/1/approve",
                tokenFor("root")).downstream()).isTrue();
    }

    @Test
    void userMessageRoutesRequireLoginButNotAdmin() {
        Invocation anonymous = invoke(HttpMethod.GET, "/api/user/messages?page=1&pageSize=10");
        assertThat(anonymous.downstream()).isFalse();
        assertThat(businessCode(anonymous.body())).isEqualTo(401);

        assertThat(invokeWithToken(HttpMethod.GET, "/api/user/messages?page=1&pageSize=10", tokenFor("student"))
                .downstream()).isTrue();
        assertThat(invokeWithToken(HttpMethod.PUT, "/api/user/messages/read-all", tokenFor("student"))
                .downstream()).isTrue();
        assertThat(invokeWithToken(HttpMethod.GET, "/api/user/profile-change-requests?page=1&pageSize=10",
                tokenFor("student")).downstream()).isTrue();
    }

    @Test
    void gatewayYamlRoutesNoticeAndProfileChangeToUserService() throws Exception {
        String pathLine = readRoutePathPredicate("hnieoj-user");
        assertThat(pathLine)
                .contains("/api/admin/notices")
                .contains("/api/admin/notices/**")
                .contains("/api/admin/profile-change-requests")
                .contains("/api/admin/profile-change-requests/**")
                .contains("/api/user/**");
    }

    /**
     * 从 hnieoj-gateway.yaml 中读取指定服务路由的 Path 谓词行。
     *
     * @param routeId 路由 id（如 hnieoj-problem）
     * @return Path 谓词行内容（去除首尾空白）
     */
    private static String readRoutePathPredicate(String routeId) throws Exception {
        Path yaml = locateRepoFile("deploy/nacos/dev/DEFAULT_GROUP/hnieoj-gateway.yaml");
        List<String> lines = Files.readAllLines(yaml);

        int routeIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().equals("- id: " + routeId)) {
                routeIndex = i;
                break;
            }
        }
        assertThat(routeIndex).as(routeId + " route must exist").isGreaterThanOrEqualTo(0);

        String pathLine = null;
        for (int i = routeIndex + 1; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.startsWith("- Path=")) {
                pathLine = trimmed;
                break;
            }
            if (trimmed.startsWith("- id: ")) {
                break;
            }
        }
        assertThat(pathLine).as(routeId + " route must declare a Path predicate").isNotNull();
        return pathLine;
    }

    private static Path locateRepoFile(String relativePath) {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("repository file not found: " + relativePath);
    }

    @Test
    void achievementFileDownloadIsAdminOrRootOnly() {
        Invocation anonymous = invoke(HttpMethod.GET, "/api/admin/achievements/1/file");
        assertThat(anonymous.downstream()).isFalse();
        assertThat(anonymous.body()).contains("401");

        Invocation student = invokeWithToken(HttpMethod.GET, "/api/admin/achievements/1/file", tokenFor("student"));
        assertThat(student.downstream()).isFalse();
        assertThat(student.body()).contains("403");

        assertThat(invokeWithToken(HttpMethod.GET, "/api/admin/achievements/1/file", tokenFor("admin")).downstream())
                .isTrue();
        assertThat(invokeWithToken(HttpMethod.GET, "/api/admin/achievements/1/file", tokenFor("root")).downstream())
                .isTrue();
    }

    private static String tokenFor(String loginId) {
        return StpUtil.createLoginSession(loginId);
    }

    private static Invocation invoke(HttpMethod method, String path) {
        return invokeWithToken(method, path, null);
    }

    private static Invocation invokeWithToken(HttpMethod method, String path, String token) {
        MockServerHttpRequest request = token == null
                ? MockServerHttpRequest.method(method, path).build()
                : MockServerHttpRequest.method(method, path)
                        .header(SaManager.getConfig().getTokenName(), token)
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean downstream = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            downstream.set(true);
            return ex.getResponse().setComplete();
        };

        filter.filter(exchange, chain).block(Duration.ofSeconds(5));
        String body = exchange.getResponse().getBodyAsString().block(Duration.ofSeconds(5));
        return new Invocation(downstream.get(), exchange.getResponse().getStatusCode(),
                exchange.getResponse().getHeaders().getContentType(), body);
    }

    private static int businessCode(String body) {
        try {
            return MAPPER.readTree(body).path("code").asInt();
        } catch (Exception e) {
            throw new AssertionError("响应体不是合法 JSON: " + body, e);
        }
    }

    private record Invocation(boolean downstream, HttpStatusCode status, MediaType contentType, String body) {
    }
}
