package com.hnieacm.judge.service.impl;

import com.hnieacm.judge.mapper.DashboardMapper;
import com.hnieacm.judge.service.DashboardService;
import com.hnieacm.judge.service.JudgeNodeOpsService;
import com.hnieacm.judge.vo.DashboardContentVo;
import com.hnieacm.judge.vo.DashboardHealthVo;
import com.hnieacm.judge.vo.DashboardMetricsVo;
import com.hnieacm.judge.vo.DashboardStatusCountVo;
import com.hnieacm.judge.vo.JudgeNodeOpsSummaryVo;
import com.hnieacm.judge.vo.ServiceStatusVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 管理端 dashboard 服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final int STATUS_PENDING = -10;
    private static final int STATUS_COMPILING = -9;
    private static final int STATUS_RUNNING = -8;
    private static final int STATUS_ACCEPTED = 0;
    private static final int STATUS_RUNTIME_ERROR = 1;
    private static final int STATUS_COMPILE_ERROR = 2;
    private static final int STATUS_WRONG_ANSWER = 3;
    private static final int STATUS_TIME_LIMIT_EXCEEDED = 4;
    private static final int STATUS_MEMORY_LIMIT_EXCEEDED = 5;
    private static final int STATUS_SYSTEM_ERROR = 6;
    private static final int STATUS_JUDGEMENT_FAILED = 7;
    private static final int STATUS_INVALID_INTERACTION = 8;

    private final DashboardMapper dashboardMapper;
    private final JudgeNodeOpsService judgeNodeOpsService;
    private final DataSource dataSource;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public DashboardMetricsVo metrics() {
        DashboardMetricsVo vo = new DashboardMetricsVo();
        vo.setTotalUsers(nonNull(dashboardMapper.countTotalUsers()));
        vo.setDau(nonNull(dashboardMapper.countTodayActiveSubmitUsers()));
        vo.setTotalProblems(nonNull(dashboardMapper.countTotalProblems()));
        vo.setTotalSubmissions(nonNull(dashboardMapper.countTotalSubmissions()));
        return vo;
    }

    @Override
    public DashboardHealthVo health() {
        long acceptedCount = nonNull(dashboardMapper.countAcceptedSubmissions());
        long terminalCount = nonNull(dashboardMapper.countTerminalSubmissions());
        long judgingCount = nonNull(dashboardMapper.countJudgingSubmissions());
        long systemErrorCount = nonNull(dashboardMapper.countSystemErrorSubmissions());
        JudgeNodeOpsSummaryVo nodeSummary = judgeNodeOpsService.summary();
        List<DashboardStatusCountVo> statusCounts = dashboardMapper.listJudgeStatusCounts();
        statusCounts.forEach(item -> item.setStatusText(toStatusText(item.getStatus())));

        DashboardHealthVo vo = new DashboardHealthVo();
        vo.setAcceptedRate(calculateRate(acceptedCount, terminalCount));
        vo.setAverageJudgeTime(defaultDouble(dashboardMapper.averageJudgeTime()));
        vo.setJudgingSubmissionCount(judgingCount);
        vo.setSystemErrorCount(systemErrorCount);
        vo.setStatusCounts(statusCounts);
        vo.setJudgeNodeSummary(nodeSummary);
        vo.setHealthy(Boolean.TRUE.equals(nodeSummary.getHealthy()) && judgingCount < 10000);
        return vo;
    }

    @Override
    public DashboardContentVo content() {
        DashboardContentVo vo = new DashboardContentVo();
        vo.setPopularProblems(dashboardMapper.listPopularProblems());
        vo.setActiveTrainings(dashboardMapper.listActiveTrainings());
        return vo;
    }

    @Override
    public List<ServiceStatusVo> services() {
        List<ServiceStatusVo> services = new ArrayList<>();
        services.add(status("Backend", "Running", "当前服务可响应请求"));
        services.add(checkMysqlStatus());
        services.add(checkRedisStatus());
        services.add(checkJudgeNodeStatus());
        return services;
    }

    private ServiceStatusVo checkMysqlStatus() {
        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(2);
            return status("MySQL", valid ? "Connected" : "Unavailable", valid ? "数据库连接可用" : "数据库连接不可用");
        } catch (Exception e) {
            log.warn("Check MySQL status failed", e);
            return status("MySQL", "Unavailable", "数据库连接异常");
        }
    }

    private ServiceStatusVo checkRedisStatus() {
        try {
            if (stringRedisTemplate.getConnectionFactory() == null) {
                return status("Redis", "Unavailable", "Redis 连接工厂未初始化");
            }
            try (RedisConnection connection = stringRedisTemplate.getConnectionFactory().getConnection()) {
                String pong = connection.ping();
                boolean connected = "PONG".equalsIgnoreCase(pong);
                return status("Redis", connected ? "Connected" : "Unavailable", connected ? "Redis 连接可用" : "Redis ping 无响应");
            }
        } catch (Exception e) {
            log.warn("Check Redis status failed", e);
            return status("Redis", "Unavailable", "Redis 连接异常");
        }
    }

    private ServiceStatusVo checkJudgeNodeStatus() {
        try {
            JudgeNodeOpsSummaryVo summary = judgeNodeOpsService.summary();
            long onlineCount = nonNull(summary.getOnlineNodeCount());
            if (onlineCount > 0) {
                return status("Judger", "Running", "在线判题节点数：" + onlineCount);
            }
            return status("Judger", "Offline", "当前没有在线判题节点");
        } catch (Exception e) {
            log.warn("Check judge node status failed", e);
            return status("Judger", "Unavailable", "判题节点状态查询异常");
        }
    }

    private ServiceStatusVo status(String name, String status, String detail) {
        ServiceStatusVo vo = new ServiceStatusVo();
        vo.setName(name);
        vo.setStatus(status);
        vo.setDetail(detail);
        return vo;
    }

    private long nonNull(Number value) {
        return value == null ? 0L : value.longValue();
    }

    private Double defaultDouble(Double value) {
        return value == null ? 0D : value;
    }

    private Double calculateRate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return numerator * 1.0D / denominator;
    }

    private String toStatusText(Integer status) {
        if (status == null) {
            return "Unknown";
        }
        return switch (status) {
            case STATUS_PENDING -> "Pending";
            case STATUS_COMPILING -> "Compiling";
            case STATUS_RUNNING -> "Running";
            case STATUS_ACCEPTED -> "Accepted";
            case STATUS_RUNTIME_ERROR -> "Runtime Error";
            case STATUS_COMPILE_ERROR -> "Compile Error";
            case STATUS_WRONG_ANSWER -> "Wrong Answer";
            case STATUS_TIME_LIMIT_EXCEEDED -> "Time Limit Exceeded";
            case STATUS_MEMORY_LIMIT_EXCEEDED -> "Memory Limit Exceeded";
            case STATUS_SYSTEM_ERROR -> "System Error";
            case STATUS_JUDGEMENT_FAILED -> "Judgement Failed";
            case STATUS_INVALID_INTERACTION -> "Invalid Interaction";
            default -> "Unknown";
        };
    }
}
