package com.hnieacm.submission.controller;

import com.hnieacm.common.result.Result;
import com.hnieacm.submission.dto.TransferSubmissionOwnerRequest;
import com.hnieacm.submission.service.SubmissionOwnerTransferService;
import com.hnieacm.submission.vo.TransferSubmissionOwnerVo;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 提交服务内部接口
 */
@Hidden
@RestController
@RequestMapping("/internal/submissions")
@RequiredArgsConstructor
public class InternalSubmissionController {

    private final SubmissionOwnerTransferService submissionOwnerTransferService;

    @PostMapping("/owner-transfer")
    public Result<TransferSubmissionOwnerVo> transferOwner(@Valid @RequestBody TransferSubmissionOwnerRequest request) {
        return Result.success(submissionOwnerTransferService.transfer(request));
    }
}
