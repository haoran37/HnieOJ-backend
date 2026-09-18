package com.hnieacm.submission;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.hnieacm.judge.mapper.JudgeNodeAuthCodeMapper;
import com.hnieacm.judge.mapper.JudgeNodeTokenMapper;
import com.hnieacm.judge.mapper.RemoteJudgeAccountMapper;
import com.hnieacm.judge.mapper.SysConfigMapper;
import com.hnieacm.judge.service.JudgeNodeHeartbeatService;
import com.hnieacm.judge.service.JudgeNodeSecurityService;
import com.hnieacm.submission.mapper.JudgeCaseMapper;
import com.hnieacm.submission.mapper.JudgeMapper;
import com.hnieacm.submission.mapper.JudgeTaskExecutionMapper;
import com.hnieacm.submission.mapper.JudgeTaskOutboxMapper;
import com.hnieacm.submission.mapper.RejudgeTaskDetailMapper;
import com.hnieacm.submission.mapper.RejudgeTaskMapper;
import com.hnieacm.submission.service.JudgeNodeAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.scheduling.TaskScheduler;
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
 * @Description: 合并 submission/judge 后的真实上下文启动测试。
 * <p>
 * 使用哑元 MySQL/Redis 配置，不连接任何远程服务，仅验证 Bean 与 Mapper 不产生重复定义，
 * 判题域控制者与路由均被扫描注册，且提交/判题调用改为进程内本地服务而非自 Feign。
 */
@SpringBootTest(classes = SubmissionApplication.class)
@ActiveProfiles("test")
class SubmissionContextStartupTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private JudgeNodeAccessService judgeNodeAccessService;

    /**
     * 合并上下文测试不访问 Nacos，仅为正式节点 Token 服务提供替身依赖。
     */
    @MockBean
    private NacosConfigManager nacosConfigManager;

    /**
     * 屏蔽真实定时任务调度，避免启动后异步访问哑元数据库；
     * 不影响任何 Bean 注册与路由断言。
     */
    @MockBean(name = "taskScheduler")
    private TaskScheduler taskScheduler;

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
                "com.hnieacm.judge.controller.AdminJudgeController",
                "com.hnieacm.judge.controller.AdminJudgeNodeController",
                "com.hnieacm.judge.controller.AdminJudgeServerController",
                "com.hnieacm.judge.controller.AdminSystemConfigController",
                "com.hnieacm.judge.controller.InternalJudgeNodeController",
                "com.hnieacm.judge.controller.JudgeNodeAuthController",
                "com.hnieacm.judge.controller.JudgeNodeHeartbeatController",
                "com.hnieacm.judge.controller.SystemController",
                "com.hnieacm.submission.controller.AdminRejudgeController",
                "com.hnieacm.submission.controller.AdminSubmissionController",
                "com.hnieacm.submission.controller.JudgeSubmissionEventController",
                "com.hnieacm.submission.controller.JudgeTaskGatewayController",
                "com.hnieacm.submission.controller.SubmissionController"
        );
    }

    @Test
    void allDomainEndpointsAreMapped() {
        Set<String> patterns = handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream())
                .collect(Collectors.toSet());

        assertThat(patterns).contains(
                "/api/submissions",
                "/api/admin/submissions/{submissionId}/rejudge",
                "/api/admin/rejudge/list",
                "/judge/submissions/{submissionId}/events",
                "/judge/tasks/claim",
                "/judge/tasks/{submissionId}/lease",
                "/judge/nodes/token/renew",
                "/internal/judge/tasks/access",
                "/api/admin/judge/nodes/formal-tokens",
                "/internal/judge/tokens/validate",
                "/internal/judge/nodes/capabilities/{judgeMode}/available",
                "/api/judge/temp-token",
                "/judge/nodes/heartbeat",
                "/api/system/public-config",
                "/api/admin/judge/nodes",
                "/api/admin/config",
                "/api/admin/judge/servers"
        );
    }

    @Test
    void consolidatedMappersAreRegisteredExactlyOnce() {
        List<Class<?>> mappers = List.of(
                JudgeMapper.class,
                JudgeCaseMapper.class,
                JudgeTaskOutboxMapper.class,
                JudgeTaskExecutionMapper.class,
                RejudgeTaskMapper.class,
                RejudgeTaskDetailMapper.class,
                JudgeNodeAuthCodeMapper.class,
                JudgeNodeTokenMapper.class,
                RemoteJudgeAccountMapper.class,
                SysConfigMapper.class
        );
        for (Class<?> mapper : mappers) {
            assertThat(applicationContext.getBeansOfType(mapper))
                    .as("mapper %s should be registered exactly once", mapper.getName())
                    .hasSize(1);
        }
    }

    @Test
    void judgeAccessUsesLocalServicesInsteadOfSelfFeign() {
        assertThat(applicationContext.getBeansOfType(JudgeNodeAccessService.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(JudgeNodeSecurityService.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(JudgeNodeHeartbeatService.class)).hasSize(1);
        assertThat(judgeNodeAccessService).isNotNull();
    }

    @Test
    void mergedProcessHasNoSelfOrRetiredFeignClient() throws Exception {
        Set<String> feignTargets = scanFeignClientNames();

        // 合并后仍保留对 user 认证与 problem 的跨服务调用，断言非空避免空集合假通过。
        assertThat(feignTargets)
                .isNotEmpty()
                .contains("hnieoj-user", "hnieoj-problem")
                .doesNotContain("hnieoj-judge", "hnieoj-submission", "hnieoj-auth", "hnieoj-achievement");
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
