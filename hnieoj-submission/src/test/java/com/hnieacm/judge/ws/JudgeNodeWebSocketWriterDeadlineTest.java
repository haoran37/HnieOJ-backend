package com.hnieacm.judge.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.judge.dto.NodeAuthChallengeVo;
import com.hnieacm.judge.properties.NodeSecurityProperties;
import com.hnieacm.judge.service.JudgeNodeLifecycleService;
import com.hnieacm.judge.service.NodeIdentityService;
import com.hnieacm.submission.service.JudgeResultReportService;
import com.hnieacm.submission.service.JudgeTaskClaimService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WSS 出站写入截止回归：单 writer 被孤立阻塞时由独立 10s 截止强制关闭，
 * 快速完成的写入必须取消截止定时器，不能因迟到 timer 误关闭健康连接。
 *
 * @author Codex
 */
class JudgeNodeWebSocketWriterDeadlineTest {

    private JudgeNodeWebSocketHandler handler(ScheduledExecutorService scheduler) {
        NodeSecurityProperties props = new NodeSecurityProperties();
        props.setAuthDeadlineSeconds(120);
        NodeIdentityService identity = mock(NodeIdentityService.class);
        when(identity.createAuthChallenge(any(), isNull(), isNull(), any(), org.mockito.ArgumentMatchers.eq(false)))
                .thenReturn(new NodeAuthChallengeVo("c", "n", "a",
                        System.currentTimeMillis() + 30_000L, System.currentTimeMillis(), "r"));
        return new JudgeNodeWebSocketHandler(identity, props, new ObjectMapper(), scheduler,
                mock(JudgeTaskClaimService.class), mock(JudgeResultReportService.class),
                mock(JudgeNodeLifecycleService.class), mock(StringRedisTemplate.class));
    }

    @Test
    void blockedWriterIsClosedByIndependentDeadline() throws Exception {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        JudgeNodeWebSocketHandler handler = handler(scheduler);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("blocked-writer-unit");
        when(session.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            entered.countDown();
            release.await();
            return null;
        }).when(session).sendMessage(any());
        try {
            handler.afterConnectionEstablished(session);
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            // 无新写入、队列未满、未到 idle/认证截止：仍必须在 10s 写截止后被关闭。
            verify(session, org.mockito.Mockito.timeout(12_000)).close();
        } finally {
            release.countDown();
            handler.shutdown();
            scheduler.shutdownNow();
        }
    }

    @Test
    void fastWriteCancelsDeadlineTimer() throws Exception {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(2);
        scheduler.setRemoveOnCancelPolicy(true);
        JudgeNodeWebSocketHandler handler = handler(scheduler);
        AtomicBoolean closed = new AtomicBoolean();
        CountDownLatch written = new CountDownLatch(1);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("fast-writer-unit");
        when(session.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            written.countDown();
            return null;
        }).when(session).sendMessage(any());
        doAnswer(invocation -> {
            closed.set(true);
            return null;
        }).when(session).close();
        try {
            handler.afterConnectionEstablished(session);
            assertThat(written.await(2, TimeUnit.SECONDS)).isTrue();
            // 写入迅速完成后定时器被取消并从有界调度队列移除，不会在 10s 后误关闭。
            long deadline = System.currentTimeMillis() + 3_000L;
            while (!scheduler.getQueue().isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20L);
            }
            assertThat(scheduler.getQueue()).isEmpty();
            assertThat(closed).isFalse();
            assertThat(handler.connectionOf("fast-writer-unit")).isNotNull();
        } finally {
            handler.shutdown();
            scheduler.shutdownNow();
        }
    }

    @Test
    void rejectedDeadlineSchedulingStopsWriteAndClosesConnection() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.shutdownNow();
        JudgeNodeWebSocketHandler handler = handler(scheduler);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("rejected-deadline-unit");
        when(session.isOpen()).thenReturn(true);
        try {
            handler.afterConnectionEstablished(session);
            // 调度器拒绝安排 deadline 时：必须立即关停连接，且不能在没有截止时间的情况下继续阻塞写入。
            verify(session, org.mockito.Mockito.timeout(2_000)).close();
            verify(session, org.mockito.Mockito.never()).sendMessage(any());
        } finally {
            handler.shutdown();
            scheduler.shutdownNow();
        }
    }
}
