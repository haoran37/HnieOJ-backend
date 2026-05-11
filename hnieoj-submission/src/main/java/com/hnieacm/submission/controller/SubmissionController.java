package com.hnieacm.submission.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.result.Result;
import com.hnieacm.submission.dto.SubmissionListQueryRequest;
import com.hnieacm.submission.dto.SubmitCodeRequest;
import com.hnieacm.submission.service.SubmissionService;
import com.hnieacm.submission.vo.SubmissionCaseVo;
import com.hnieacm.submission.vo.SubmissionDetailVo;
import com.hnieacm.submission.vo.SubmissionListItemVo;
import com.hnieacm.submission.vo.SubmitCodeVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 提交接口
 */
@Tag(name = "提交模块")
@Validated
@SaCheckLogin
@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @Operation(summary = "获取提交记录列表")
    @GetMapping
    public Result<PageVo<SubmissionListItemVo>> list(@Valid SubmissionListQueryRequest request) {
        return Result.success(submissionService.listSubmissions(request));
    }

    @Operation(summary = "获取提交记录详情")
    @GetMapping("/{submissionId}")
    public Result<SubmissionDetailVo> detail(@PathVariable String submissionId) {
        return Result.success(submissionService.getSubmissionDetail(submissionId));
    }

    @Operation(summary = "获取提交测试点结果")
    @GetMapping("/{submissionId}/cases")
    public Result<List<SubmissionCaseVo>> cases(@PathVariable String submissionId) {
        return Result.success(submissionService.listSubmissionCases(submissionId));
    }

    @Operation(summary = "提交代码（JSON）")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<SubmitCodeVo> submitJson(@Valid @RequestBody SubmitCodeRequest request) {
        return Result.success("提交成功", submissionService.submit(request, null));
    }

    @Operation(summary = "提交代码（multipart/form-data）")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<SubmitCodeVo> submitForm(@RequestParam("problemCode") String problemCode,
                                           @RequestParam String language,
                                           @RequestParam(required = false) String code,
                                           @RequestPart(required = false) MultipartFile file,
                                           @RequestParam(required = false) String contestId) {
        SubmitCodeRequest request = new SubmitCodeRequest();
        request.setProblemCode(problemCode);
        request.setLanguage(language);
        request.setCode(code);
        request.setContestId(contestId);
        return Result.success("提交成功", submissionService.submit(request, file));
    }
}
