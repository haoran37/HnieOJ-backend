package com.hnieacm.submission.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.hnieacm.common.result.Result;
import com.hnieacm.submission.dto.SubmitCodeRequest;
import com.hnieacm.submission.service.SubmissionService;
import com.hnieacm.submission.vo.SubmitCodeVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 提交接口
 */
@Tag(name = "提交模块")
@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @Operation(summary = "提交代码（JSON）")
    @SaCheckLogin
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<SubmitCodeVo> submitJson(@Valid @RequestBody SubmitCodeRequest request) {
        return Result.success("提交成功", submissionService.submit(request, null));
    }

    @Operation(summary = "提交代码（multipart/form-data）")
    @SaCheckLogin
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
