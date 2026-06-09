package com.hnieacm.judge.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.result.Result;
import com.hnieacm.judge.service.DashboardService;
import com.hnieacm.judge.vo.DashboardContentVo;
import com.hnieacm.judge.vo.DashboardHealthVo;
import com.hnieacm.judge.vo.DashboardMetricsVo;
import com.hnieacm.judge.vo.ServiceStatusVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 管理端 dashboard 接口
 */
@Tag(name = "管理端 dashboard 模块")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@SaCheckRole(value = {RoleConstant.ADMIN, RoleConstant.ROOT}, mode = SaMode.OR)
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "获取核心指标")
    @GetMapping("/dashboard/metrics")
    public Result<DashboardMetricsVo> metrics() {
        return Result.success(dashboardService.metrics());
    }

    @Operation(summary = "获取系统健康度")
    @GetMapping("/dashboard/health")
    public Result<DashboardHealthVo> health() {
        return Result.success(dashboardService.health());
    }

    @Operation(summary = "获取热门内容")
    @GetMapping("/dashboard/content")
    public Result<DashboardContentVo> content() {
        return Result.success(dashboardService.content());
    }

    @Operation(summary = "获取服务状态")
    @GetMapping("/system/services")
    public Result<List<ServiceStatusVo>> services() {
        return Result.success(dashboardService.services());
    }
}
