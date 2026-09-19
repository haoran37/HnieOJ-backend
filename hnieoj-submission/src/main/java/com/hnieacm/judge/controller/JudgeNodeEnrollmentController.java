package com.hnieacm.judge.controller;

import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.judge.dto.NodeEnrollRequest;
import com.hnieacm.judge.dto.NodeEnrollmentChallengeRequest;
import com.hnieacm.judge.dto.NodeKeyRotationConfirmRequest;
import com.hnieacm.judge.dto.NodeKeyRotationPrepareRequest;
import com.hnieacm.judge.service.NodeIdentityService;
import com.hnieacm.judge.service.NodeKeyRotationService;
import com.hnieacm.judge.service.NodeSignedHttpService;
import com.hnieacm.judge.service.SignedCaller;
import com.hnieacm.judge.vo.NodeEnrollVo;
import com.hnieacm.judge.vo.NodeEnrollmentChallengeVo;
import com.hnieacm.judge.vo.NodeKeyRotationVo;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 节点身份协议 v1 REST 入口：注册与密钥轮换。
 *
 * <p>这些端点位于网关 public 路径，自身通过 Bootstrap 一次性凭据或
 * NODE_ACCESS + Ed25519 HTTP 签名完成鉴权，不依赖用户登录态。</p>
 *
 * @author Codex
 */
@Hidden
@Validated
@RestController
@RequestMapping("/judge/nodes")
@RequiredArgsConstructor
public class JudgeNodeEnrollmentController {

    private final NodeIdentityService nodeIdentityService;
    private final NodeKeyRotationService nodeKeyRotationService;
    private final NodeSignedHttpService nodeSignedHttpService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @PostMapping("/enrollment-challenges")
    public Result<NodeEnrollmentChallengeVo> enrollmentChallenges(
            @Valid @RequestBody NodeEnrollmentChallengeRequest request,
            HttpServletRequest servletRequest) {
        return Result.success(nodeIdentityService.createEnrollmentChallenge(request,
                servletRequest.getRemoteAddr()));
    }

    @PostMapping("/enroll")
    public Result<NodeEnrollVo> enroll(@Valid @RequestBody NodeEnrollRequest request) {
        return Result.success(nodeIdentityService.enroll(request));
    }

    @PostMapping("/keys/rotations")
    public Result<NodeKeyRotationVo> prepareRotation(@RequestBody byte[] body, HttpServletRequest servletRequest) {
        SignedCaller caller = nodeSignedHttpService.authorize(servletRequest, body);
        return Result.success(nodeKeyRotationService.prepare(caller,
                readBody(body, NodeKeyRotationPrepareRequest.class)));
    }

    @PostMapping("/keys/rotations/{rotationId}/confirm")
    public Result<NodeKeyRotationVo> confirmRotation(@PathVariable String rotationId,
                                                     @RequestBody byte[] body,
                                                     HttpServletRequest servletRequest) {
        SignedCaller caller = nodeSignedHttpService.authorize(servletRequest, body);
        NodeKeyRotationConfirmRequest request = readBody(body, NodeKeyRotationConfirmRequest.class);
        return Result.success(nodeKeyRotationService.confirm(caller, rotationId, request.getSignature()));
    }

    @GetMapping("/keys/rotations/{rotationId}")
    public Result<NodeKeyRotationVo> queryRotation(@PathVariable String rotationId,
                                                   HttpServletRequest servletRequest) {
        SignedCaller caller = nodeSignedHttpService.authorize(servletRequest, new byte[0]);
        return Result.success(nodeKeyRotationService.query(caller, rotationId));
    }

    private <T> T readBody(byte[] body, Class<T> type) {
        T value;
        try {
            value = objectMapper.readValue(body, type);
        } catch (Exception e) {
            throw new BizException(ResultCode.BAD_REQUEST, "请求体格式不合法");
        }
        // 手工反序列化的 body 不会经过 @Valid，需显式执行 Bean Validation。
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, violations.iterator().next().getMessage());
        }
        return value;
    }
}
