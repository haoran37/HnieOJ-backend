package com.hnieacm.submission.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.hnieacm.common.constant.PermissionConstant;
import com.hnieacm.common.result.Result;
import com.hnieacm.submission.service.SubmissionService;
import com.hnieacm.submission.vo.SubmitCodeVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @Operation(summary = "重判单个提交")
    @SaCheckPermission(PermissionConstant.PROBLEM_UPDATE)
    @PostMapping("/{submissionId}/rejudge")
    public Result<SubmitCodeVo> rejudge(@PathVariable String submissionId) {
        return Result.success("重判任务已提交", submissionService.rejudgeSubmission(submissionId));
    }
}
