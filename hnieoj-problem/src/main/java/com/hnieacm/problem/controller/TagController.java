package com.hnieacm.problem.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.hnieacm.common.result.Result;
import com.hnieacm.problem.service.TagService;
import com.hnieacm.problem.vo.TagVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 标签目录接口（登录可读）
 */
@Tag(name = "标签模块")
@Validated
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @Operation(summary = "获取标签目录")
    @SaCheckLogin
    @GetMapping
    public Result<List<TagVo>> list() {
        // hnieoj-problem 未注册 SaInterceptor，注解不会生效；显式校验登录，与网关规则一致。
        StpUtil.checkLogin();
        return Result.success(tagService.listTags());
    }
}
