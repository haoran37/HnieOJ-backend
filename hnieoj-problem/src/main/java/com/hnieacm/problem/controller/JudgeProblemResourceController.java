package com.hnieacm.problem.controller;

import com.hnieacm.common.dto.JudgeTaskAccessRequest;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.judge.NodeProtocolConstants;
import com.hnieacm.common.judge.NodeSignatureCodec;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.problem.service.JudgeNodeAccessService;
import com.hnieacm.problem.service.ProblemResourceService;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 判题节点题目资源接口（原样提取 HTTP 签名上下文，鉴权先于 304/正文返回）
 */
@Validated
@RestController
@RequestMapping("/judge/problems")
@RequiredArgsConstructor
public class JudgeProblemResourceController {

    private static final String DATA_VERSION_HEADER = "X-Data-Version";
    private static final String TESTDATA_ZIP_FILENAME = "testdata.zip";
    private static final String APPLICATION_ZIP_VALUE = "application/zip";
    /** 签名请求体上限：GET 下载正常为空体，绝不无上限缓冲未认证字节。 */
    private static final int MAX_SIGNED_BODY_BYTES = 65536;

    private final JudgeNodeAccessService judgeNodeAccessService;
    private final ProblemResourceService problemResourceService;
    private final ObjectMapper objectMapper;

    @GetMapping("/{id}/testdata")
    public void downloadTestdata(@PathVariable @Min(value = 1, message = "id 必须大于 0") Long id,
                                 @RequestParam(required = false) Integer version,
                                 @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                 HttpServletRequest request,
                                 HttpServletResponse response) {
        try {
            // 304 与错误路径同样必须先完成鉴权，不能凭 bearer 或篡改 problemId 放行。
            judgeNodeAccessService.checkAccess(buildAccessContext(id, authorization, request));
            ProblemResourceService.TestdataDownloadDecision decision =
                    problemResourceService.prepareTestdataDownload(id, version);
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
        } catch (BizException e) {
            writeBizError(response, e);
        }
    }

    /**
     * 提取原始 method/pathWithRawQuery/body 摘要与签名头，保持签名字节不被重新序列化。
     */
    private JudgeTaskAccessRequest buildAccessContext(Long problemId, String authorization,
                                                      HttpServletRequest request) {
        byte[] body = readRawBody(request);
        JudgeTaskAccessRequest access = new JudgeTaskAccessRequest();
        access.setAuthorization(authorization);
        access.setNodeId(request.getHeader(NodeProtocolConstants.HEADER_NODE_ID));
        access.setKeyId(request.getHeader(NodeProtocolConstants.HEADER_KEY_ID));
        access.setTimestamp(request.getHeader(NodeProtocolConstants.HEADER_TIMESTAMP));
        access.setNonce(request.getHeader(NodeProtocolConstants.HEADER_NONCE));
        access.setSignature(request.getHeader(NodeProtocolConstants.HEADER_SIGNATURE));
        access.setMethod(request.getMethod());
        access.setPathWithQuery(rawPathWithQuery(request));
        access.setBodySha256(NodeSignatureCodec.sha256Hex(body));
        access.setSubmissionId(request.getParameter("submissionId"));
        access.setJudgeTaskId(request.getParameter("judgeTaskId"));
        access.setAttemptId(request.getParameter("attemptId"));
        access.setProblemId(problemId);
        return access;
    }

    /**
     * 有界读取原始请求体：签名必须覆盖实际收到字节，但绝不 {@code readAllBytes} 任意未认证输入。
     * 超过上限直接拒绝，避免内存被超大 GET 体挤占。
     */
    private byte[] readRawBody(HttpServletRequest request) {
        byte[] buffer = new byte[4096];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ServletInputStream input = request.getInputStream()) {
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_SIGNED_BODY_BYTES) {
                    throw new BizException(ResultCode.BAD_REQUEST, "请求体超过签名上限");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "请求体读取失败");
        }
    }

    private String rawPathWithQuery(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        return query == null || query.isEmpty() ? uri : uri + "?" + query;
    }

    private OutputStream getOutputStream(HttpServletResponse response) {
        try {
            return response.getOutputStream();
        } catch (IOException e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "响应流创建失败");
        }
    }

    private void writeBizError(HttpServletResponse response, BizException e) {
        response.reset();
        response.setStatus(toHttpStatus(e.getCode()));
        response.setContentType("application/json;charset=UTF-8");
        try {
            objectMapper.writeValue(response.getWriter(), Result.error(e.getCode(), e.getMsg()));
        } catch (IOException ioException) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "响应流写入失败");
        }
    }

    private int toHttpStatus(int code) {
        if (code == ResultCode.BAD_REQUEST || code == ResultCode.UNAUTHORIZED
                || code == ResultCode.FORBIDDEN || code == ResultCode.NOT_FOUND) {
            return code;
        }
        if (code == ResultCode.PROBLEM_NOT_FOUND) {
            return ResultCode.NOT_FOUND;
        }
        return ResultCode.INTERNAL_ERROR;
    }
}
