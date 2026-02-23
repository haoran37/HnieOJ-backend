package com.hnieacm.training.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.result.Result;
import com.hnieacm.training.service.HomeworkQueryService;
import com.hnieacm.training.vo.HomeworkDetailVo;
import com.hnieacm.training.vo.HomeworkListVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 作业模块对外接口
 */
@Tag(name = "作业模块")
@Validated
@SaCheckLogin
@RestController
@RequestMapping("/api/homeworks")
@RequiredArgsConstructor
public class HomeworkController {

    private final HomeworkQueryService homeworkQueryService;

    @Operation(summary = "获取作业列表")
    @GetMapping
    public Result<PageVo<HomeworkListVo>> list(
            @RequestParam @Min(value = 1, message = "page 必须大于等于 1") int page,
            @RequestParam @Min(value = 1, message = "pageSize 必须大于等于 1") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(homeworkQueryService.listHomeworks(page, pageSize, keyword));
    }

    @Operation(summary = "获取作业详情")
    @GetMapping("/{id}")
    public Result<HomeworkDetailVo> detail(@PathVariable("id") @Min(value = 1, message = "id 必须大于等于 1") Long homeworkId) {
        return Result.success(homeworkQueryService.getHomeworkDetail(homeworkId));
    }
}
