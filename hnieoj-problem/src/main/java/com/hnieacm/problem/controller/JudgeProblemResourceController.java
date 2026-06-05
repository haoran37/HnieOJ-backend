package com.hnieacm.problem.controller;

import com.hnieacm.common.constant.HeaderConstant;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.problem.service.JudgeNodeAccessService;
import com.hnieacm.problem.service.ProblemResourceService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 判题节点题目资源接口
 */
@Validated
@RestController
@RequestMapping("/judge/problems")
@RequiredArgsConstructor
public class JudgeProblemResourceController {

    private static final String DATA_VERSION_HEADER = "X-Data-Version";
    private static final String TESTDATA_ZIP_FILENAME = "testdata.zip";
    private static final String APPLICATION_ZIP_VALUE = "application/zip";

    private final JudgeNodeAccessService judgeNodeAccessService;
    private final ProblemResourceService problemResourceService;

    @GetMapping("/{id}/testdata")
    public void downloadTestdata(@PathVariable @Min(value = 1, message = "id 必须大于 0") Long id,
                                 @RequestParam(required = false) Integer version,
                                 @RequestHeader(value = HeaderConstant.JUDGE_TOKEN, required = false) String judgeToken,
                                 @RequestHeader(value = HeaderConstant.AUTHORIZATION, required = false) String authorization,
                                 HttpServletResponse response) {
        judgeNodeAccessService.checkAccess(judgeToken, authorization);
        ProblemResourceService.TestdataDownloadDecision decision = problemResourceService.prepareTestdataDownload(id, version);
        if (decision.notModified()) {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            response.setHeader(DATA_VERSION_HEADER, String.valueOf(decision.dataVersion()));
            return;
        }

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(APPLICATION_ZIP_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + TESTDATA_ZIP_FILENAME + "\"");
        response.setHeader(DATA_VERSION_HEADER, String.valueOf(decision.dataVersion()));
        problemResourceService.writeTestdataZip(id, getOutputStream(response));
    }

    private OutputStream getOutputStream(HttpServletResponse response) {
        try {
            return response.getOutputStream();
        } catch (IOException e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "响应流创建失败");
        }
    }
}
