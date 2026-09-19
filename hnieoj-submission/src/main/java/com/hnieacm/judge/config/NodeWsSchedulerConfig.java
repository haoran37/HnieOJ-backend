package com.hnieacm.judge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

/**
 * 节点 WebSocket 认证截止调度器。
 *
 * <p>独立于每连接写线程，负责在认证截止时间强制关闭未认证连接；shutdown 时释放。</p>
 *
 * @author Codex
 */
@Configuration
public class NodeWsSchedulerConfig {

    private static final int AUTH_TIMER_THREADS = 2;
    private static final String AUTH_TIMER_THREAD_PREFIX = "judge-node-auth-timer-";
    private static final int DISPATCH_THREADS = 1;
    private static final String DISPATCH_THREAD_PREFIX = "judge-node-dispatch-";

    /**
     * 有界认证定时调度器，容器关闭时释放线程。
     *
     * @return 调度器
     */
    @Bean(destroyMethod = "shutdownNow")
    public ScheduledExecutorService nodeWsScheduler() {
        ThreadFactory threadFactory = new CustomizableThreadFactory(AUTH_TIMER_THREAD_PREFIX);
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(AUTH_TIMER_THREADS, threadFactory);
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    /**
     * 节点任务调度专用单线程调度器：与认证定时隔离，容器关闭时释放。
     *
     * @return 调度器
     */
    @Bean(destroyMethod = "shutdownNow")
    public ScheduledExecutorService judgeNodeDispatchScheduler() {
        ThreadFactory threadFactory = new CustomizableThreadFactory(DISPATCH_THREAD_PREFIX);
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(DISPATCH_THREADS, threadFactory);
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }
}
