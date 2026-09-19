package com.hnieacm.judge.ws;

import com.hnieacm.common.exception.BizException;
import com.hnieacm.judge.service.SignedCaller;
import com.hnieacm.submission.service.JudgeTaskClaimService;
import com.hnieacm.submission.vo.JudgeTaskClaimVo;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 本实例 READY 节点的判题任务调度器。
 *
 * <p>每个 submission 实例只调度自己持有的 READY 连接，从共享 Redis Streams 有界领取；
 * DB 在 claim 事务内核准额度与所有权，因此这里不做额度判定，只负责顺序与公平性。
 * 采用轮转起始游标实现节点间的公平轮询，并按节点权重给出每轮上限，避免总是第一个节点独占。
 * 慢连接由每连接有界 writer 隔离；发送失败时 DB 租约保留，交由恢复流程回收。</p>
 *
 * @author Codex
 */
@Slf4j
@Component
public class JudgeNodeTaskDispatcher {

    private static final long DISPATCH_INTERVAL_MILLIS = 200L;
    private static final int DEFAULT_ROUND_BUDGET = 8;

    private final JudgeNodeWebSocketHandler webSocketHandler;
    private final JudgeTaskClaimService judgeTaskClaimService;
    private final ScheduledExecutorService dispatchScheduler;
    private final AtomicInteger rotationCursor = new AtomicInteger();

    private volatile ScheduledFuture<?> scheduledTask;

    public JudgeNodeTaskDispatcher(JudgeNodeWebSocketHandler webSocketHandler,
                                   JudgeTaskClaimService judgeTaskClaimService,
                                   @Qualifier("judgeNodeDispatchScheduler")
                                   ScheduledExecutorService dispatchScheduler) {
        this.webSocketHandler = webSocketHandler;
        this.judgeTaskClaimService = judgeTaskClaimService;
        this.dispatchScheduler = dispatchScheduler;
    }

    @PostConstruct
    public void start() {
        this.scheduledTask = dispatchScheduler.scheduleWithFixedDelay(this::safeDispatchRound,
                DISPATCH_INTERVAL_MILLIS, DISPATCH_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void stop() {
        ScheduledFuture<?> task = this.scheduledTask;
        if (task != null) {
            task.cancel(true);
        }
    }

    private void safeDispatchRound() {
        try {
            dispatchRound();
        } catch (Exception e) {
            log.warn("judge node dispatch round failed: {}", e.getMessage());
        }
    }

    /**
     * 有界加权公平调度：每轮先按轮转游标选起始节点，再按 DB 权重给出每个节点的本轮上限，
     * 权重最低也保底 1 个槽位（不饥饿），总下发量受 round budget 约束。
     * 真正可发数量还受该连接 READY credit（availableSlots）限制。
     */
    void dispatchRound() {
        List<JudgeNodeWebSocketHandler.NodeConnection> ready = webSocketHandler.readyConnections();
        if (ready.isEmpty()) {
            return;
        }
        int size = ready.size();
        int start = Math.floorMod(rotationCursor.getAndIncrement(), size);
        int maxWeight = 1;
        for (JudgeNodeWebSocketHandler.NodeConnection connection : ready) {
            maxWeight = Math.max(maxWeight, connection.weight());
        }
        int dispatched = 0;
        for (int i = 0; i < size && dispatched < DEFAULT_ROUND_BUDGET; i++) {
            JudgeNodeWebSocketHandler.NodeConnection connection = ready.get((start + i) % size);
            if (!connection.isReady()) {
                continue;
            }
            int share = weightedShare(connection.weight(), maxWeight);
            int budget = Math.min(Math.min(connection.availableSlots(), share),
                    DEFAULT_ROUND_BUDGET - dispatched);
            for (int n = 0; n < budget; n++) {
                JudgeTaskClaimVo claim;
                try {
                    claim = judgeTaskClaimService.claim(new SignedCaller(connection.nodeId(), connection.keyId(),
                            connection.accessVersion(), connection.sessionEpoch()));
                } catch (BizException e) {
                    // 会话被接管/节点失效时停止该节点本轮调度，不伪装成功。
                    break;
                } catch (Exception e) {
                    log.warn("judge node claim failed, nodeId: {}, msg: {}", connection.nodeId(), e.getMessage());
                    break;
                }
                if (claim == null) {
                    break;
                }
                if (webSocketHandler.sendTaskAssign(connection, claim)) {
                    dispatched++;
                } else {
                    // credit 已耗尽：停止该节点本轮，避免空转领取。
                    break;
                }
            }
        }
    }

    /**
     * 按权重计算单节点本轮上限：低权重保底 1，避免饿死；结果受 round budget 封顶。
     */
    int weightedShare(int weight, int maxWeight) {
        if (maxWeight <= 0) {
            return 1;
        }
        long share = Math.max(1L, (long) weight * DEFAULT_ROUND_BUDGET / maxWeight);
        return (int) Math.min(DEFAULT_ROUND_BUDGET, share);
    }
}
