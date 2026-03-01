package com.hnieacm.judge.controller;

import com.hnieacm.common.result.Result;
import com.hnieacm.judge.service.SystemConfigService;
import com.hnieacm.judge.vo.SystemPublicConfigVo;
import com.hnieacm.judge.vo.SystemTimeVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/03/01
 * @Description: 系统公开接口
 */
@Tag(name = "系统模块（公开）")
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final SystemConfigService systemConfigService;

    @Operation(summary = "获取系统公开信息")
    @GetMapping("/public-config")
    public Result<SystemPublicConfigVo> publicConfig() {
        return Result.success(systemConfigService.getPublicConfig());
    }

    @Operation(summary = "获取系统时间")
    @GetMapping("/time")
    public Result<SystemTimeVo> systemTime() {
        return Result.success(systemConfigService.getSystemTime());
    }
}
