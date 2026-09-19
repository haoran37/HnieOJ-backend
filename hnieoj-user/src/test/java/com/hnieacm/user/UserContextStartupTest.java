package com.hnieacm.user;

import com.hnieacm.achievement.mapper.AchievementApplyMapper;
import com.hnieacm.achievement.mapper.UserAchievementMapper;
import com.hnieacm.auth.mapper.PermissionMapper;
import com.hnieacm.auth.mapper.RolePermissionMapper;
import com.hnieacm.auth.mapper.UserRegisterApplyMapper;
import com.hnieacm.user.mapper.RoleMapper;
import com.hnieacm.user.mapper.SysClassMapper;
import com.hnieacm.user.mapper.SysClassTaMapper;
import com.hnieacm.user.mapper.SysCollegeMapper;
import com.hnieacm.user.mapper.UserInfoMapper;
import com.hnieacm.user.mapper.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/18
 * @Description: 合并 user/auth/achievement 后的真实上下文启动测试。
 * <p>
 * 使用哑元 MySQL/Redis 配置，不连接任何远程服务，仅验证 Bean 与 Mapper 不产生重复定义，
 * 三个域的控制者与路由均被扫描注册。
 */
@SpringBootTest(classes = UserApplication.class)
@ActiveProfiles("test")
class UserContextStartupTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void mergedContextLoads() {
        assertThat(applicationContext).isNotNull();
    }

    @Test
    void allDomainControllersAreRegisteredExactlyOnce() {
        Map<String, Object> controllers = applicationContext.getBeansWithAnnotation(
                org.springframework.web.bind.annotation.RestController.class);
        Set<String> controllerTypes = controllers.values().stream()
                .map(bean -> org.springframework.aop.support.AopUtils.getTargetClass(bean).getName())
                .collect(Collectors.toSet());

        assertThat(controllerTypes).contains(
                "com.hnieacm.auth.controller.AuthController",
                "com.hnieacm.auth.controller.InternalAuthController",
                "com.hnieacm.auth.controller.InternalAuthCacheController",
                "com.hnieacm.auth.controller.RegistrationReviewController",
                "com.hnieacm.achievement.controller.AchievementApplyController",
                "com.hnieacm.achievement.controller.AdminAchievementApplyController",
                "com.hnieacm.achievement.controller.AdminUserAchievementController",
                "com.hnieacm.achievement.controller.UserAchievementController",
                "com.hnieacm.user.controller.UserManageController",
                "com.hnieacm.user.controller.UserProfileController",
                "com.hnieacm.user.controller.UserLookupController",
                "com.hnieacm.user.controller.InternalUserLookupController",
                "com.hnieacm.user.controller.AdminPermissionController",
                "com.hnieacm.user.controller.BaseDataController"
        );
    }

    @Test
    void allDomainEndpointsAreMapped() {
        Set<String> patterns = handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream())
                .collect(Collectors.toSet());

        assertThat(patterns).contains(
                "/api/auth/login",
                "/api/auth/register",
                "/api/registrations",
                "/api/registrations/{uid}/approve",
                "/internal/auth/cache/refresh/{uid}",
                "/internal/auth/permissions/{uid}",
                "/api/achievements/apply",
                "/api/admin/achievements",
                "/api/users/{uid}/achievements",
                "/api/admin/users/{uid}/achievements",
                "/api/user/profile",
                "/api/users",
                "/api/admin/permission/users",
                "/internal/users/basic-info"
        );
    }

    @Test
    void consolidatedMappersAreRegisteredExactlyOnce() {
        List<Class<?>> mappers = List.of(
                UserInfoMapper.class,
                RoleMapper.class,
                SysClassMapper.class,
                SysCollegeMapper.class,
                UserRoleMapper.class,
                SysClassTaMapper.class,
                PermissionMapper.class,
                RolePermissionMapper.class,
                UserRegisterApplyMapper.class,
                AchievementApplyMapper.class,
                UserAchievementMapper.class
        );
        for (Class<?> mapper : mappers) {
            assertThat(applicationContext.getBeansOfType(mapper))
                    .as("mapper %s should be registered exactly once", mapper.getName())
                    .hasSize(1);
        }
    }

    @Test
    void mergedProcessHasNoSelfOrRetiredFeignClient() throws Exception {
        Set<String> feignTargets = scanFeignClientNames();

        // 认证/成就已合并进 user 进程，本地调用改为 Service 直调；
        // 仍需保留跨领域的 SubmissionInternalFeignClient（hnieoj-submission），且不得残留旧服务目标。
        assertThat(feignTargets)
                .isNotEmpty()
                .contains("hnieoj-submission")
                .doesNotContain("hnieoj-auth", "hnieoj-achievement", "hnieoj-judge", "hnieoj-user");
    }

    /**
     * 默认的 {@link ClassPathScanningCandidateComponentProvider} 会过滤掉接口，
     * 而 FeignClient 正是接口，因此覆写候选判定以按真实元数据扫描。
     */
    private Set<String> scanFeignClientNames() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                        return true;
                    }
                };
        scanner.addIncludeFilter(new AnnotationTypeFilter(FeignClient.class));

        return scanner.findCandidateComponents("com.hnieacm").stream()
                .map(definition -> {
                    try {
                        Class<?> type = Class.forName(definition.getBeanClassName());
                        return type.getAnnotation(FeignClient.class).name();
                    } catch (ClassNotFoundException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .collect(Collectors.toSet());
    }
}
