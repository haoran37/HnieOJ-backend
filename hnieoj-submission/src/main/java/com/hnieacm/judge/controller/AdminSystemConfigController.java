package com.hnieacm.judge.controller;

import com.hnieacm.common.result.Result;
import com.hnieacm.judge.dto.SystemConfigSaveRequest;
import com.hnieacm.judge.service.SystemConfigService;
import com.hnieacm.judge.vo.SystemConfigVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/03/01
 * @Description: 系统配置管理接口（管理员）
 */
@Tag(name = "系统配置管理模块（管理员）")
@Validated
@RestController
@RequestMapping("/api/admin/config")
@RequiredArgsConstructor
public class AdminSystemConfigController {

    private final SystemConfigService systemConfigService;

    @Operation(summary = "获取系统配置")
    @GetMapping
    public Result<SystemConfigVo> getConfig() {
        return Result.success(systemConfigService.getSystemConfig());
    }

    @Operation(summary = "保存系统配置")
    @PutMapping
    public Result<Void> saveConfig(@Valid @RequestBody SystemConfigSaveRequest request) {
        systemConfigService.saveSystemConfig(request);
        return Result.success("配置保存成功", null);
    }
}
