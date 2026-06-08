package com.hnieacm.submission.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.hnieacm.common.constant.PermissionConstant;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.result.Result;
import com.hnieacm.submission.dto.CreateRejudgeTaskRequest;
import com.hnieacm.submission.dto.JudgeTaskOutboxQueryRequest;
import com.hnieacm.submission.dto.RejudgeTaskQueryRequest;
import com.hnieacm.submission.service.JudgeOpsService;
import com.hnieacm.submission.service.JudgeTaskOutboxService;
import com.hnieacm.submission.service.RejudgeTaskService;
import com.hnieacm.submission.service.SubmissionService;
import com.hnieacm.submission.vo.JudgeOpsSummaryVo;
import com.hnieacm.submission.vo.JudgeTaskOutboxVo;
import com.hnieacm.submission.vo.RejudgeTaskVo;
import com.hnieacm.submission.vo.SubmitCodeVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 后台提交管理接口
 */
@Tag(name = "后台提交管理")
@Validated
@SaCheckLogin
@RestController
@RequestMapping("/api/admin/submissions")
@RequiredArgsConstructor
public class AdminSubmissionController {

    private final SubmissionService submissionService;
    private final JudgeTaskOutboxService judgeTaskOutboxService;
    private final RejudgeTaskService rejudgeTaskService;
    private final JudgeOpsService judgeOpsService;

    @Operation(summary = "查询判题链路运维摘要")
    @SaCheckPermission(PermissionConstant.PROBLEM_UPDATE)
    @GetMapping("/judge-ops/summary")
    public Result<JudgeOpsSummaryVo> judgeOpsSummary() {
        return Result.success(judgeOpsService.summary());
    }

    @Operation(summary = "重判单个提交")
    @SaCheckPermission(PermissionConstant.PROBLEM_UPDATE)
    @PostMapping("/{submissionId}/rejudge")
    public Result<SubmitCodeVo> rejudge(@PathVariable String submissionId) {
        return Result.success("重判任务已提交", submissionService.rejudgeSubmission(submissionId));
    }

    @Operation(summary = "查询判题任务 outbox")
    @SaCheckPermission(PermissionConstant.PROBLEM_UPDATE)
    @GetMapping("/judge-outbox")
    public Result<PageVo<JudgeTaskOutboxVo>> listJudgeOutbox(@Valid JudgeTaskOutboxQueryRequest request) {
        return Result.success(judgeTaskOutboxService.list(request));
    }

    @Operation(summary = "手动重试判题任务 outbox")
    @SaCheckPermission(PermissionConstant.PROBLEM_UPDATE)
    @PostMapping("/judge-outbox/{id}/retry")
    public Result<Void> retryJudgeOutbox(@PathVariable Long id) {
        judgeTaskOutboxService.retry(id);
        return Result.success("重试任务已提交", null);
    }

    @Operation(summary = "创建批量重判任务")
    @SaCheckPermission(PermissionConstant.PROBLEM_UPDATE)
    @PostMapping("/rejudge-tasks")
    public Result<RejudgeTaskVo> createRejudgeTask(@Valid @RequestBody CreateRejudgeTaskRequest request) {
        return Result.success("重判任务已创建", rejudgeTaskService.create(request));
    }

    @Operation(summary = "查询批量重判任务")
    @SaCheckPermission(PermissionConstant.PROBLEM_UPDATE)
    @GetMapping("/rejudge-tasks")
    public Result<PageVo<RejudgeTaskVo>> listRejudgeTasks(@Valid RejudgeTaskQueryRequest request) {
        return Result.success(rejudgeTaskService.list(request));
    }
}
