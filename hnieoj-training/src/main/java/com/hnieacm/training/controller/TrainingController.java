package com.hnieacm.training.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.result.Result;
import com.hnieacm.training.service.TrainingQueryService;
import com.hnieacm.training.vo.TrainingDetailVo;
import com.hnieacm.training.vo.TrainingListVo;
import com.hnieacm.training.vo.TrainingProblemVo;
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
 * @Description: 题单模块对外接口
 */
@Tag(name = "题单模块")
@Validated
@SaCheckLogin
@RestController
@RequestMapping("/api/trainings")
@RequiredArgsConstructor
public class TrainingController {

    private final TrainingQueryService trainingQueryService;

    @Operation(summary = "获取题单列表")
    @GetMapping
    public Result<PageVo<TrainingListVo>> list(
            @RequestParam @Min(value = 1, message = "page 必须大于等于 1") int page,
            @RequestParam @Min(value = 1, message = "pageSize 必须大于等于 1") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String auth) {
        return Result.success(trainingQueryService.listTrainings(page, pageSize, keyword, type, auth));
    }

    @Operation(summary = "获取题单详情")
    @GetMapping("/{id}/information")
    public Result<TrainingDetailVo> detail(@PathVariable("id") @Min(value = 1, message = "id 必须大于等于 1") Long trainingId) {
        return Result.success(trainingQueryService.getTrainingDetail(trainingId));
    }

    @Operation(summary = "获取题单题目列表")
    @GetMapping("/{id}/problems")
    public Result<PageVo<TrainingProblemVo>> listProblems(
            @PathVariable("id") @Min(value = 1, message = "id 必须大于等于 1") Long trainingId,
            @RequestParam @Min(value = 1, message = "page 必须大于等于 1") int page,
            @RequestParam @Min(value = 1, message = "pageSize 必须大于等于 1") int pageSize) {
        return Result.success(trainingQueryService.listTrainingProblems(trainingId, page, pageSize));
    }
}
