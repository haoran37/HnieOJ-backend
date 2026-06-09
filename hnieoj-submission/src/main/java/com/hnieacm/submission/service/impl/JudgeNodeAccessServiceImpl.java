package com.hnieacm.submission.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hnieacm.common.dto.JudgeNodeRequestContext;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.common.util.JudgeNodeRequestContextUtils;
import com.hnieacm.submission.dto.ValidateJudgeNodeTokenRequest;
import com.hnieacm.submission.feign.JudgeNodeTokenFeignClient;
import com.hnieacm.submission.service.JudgeNodeAccessService;
import com.hnieacm.submission.vo.JudgeNodeTokenValidationVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 判题节点访问校验服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeNodeAccessServiceImpl implements JudgeNodeAccessService {

    private final JudgeNodeTokenFeignClient judgeNodeTokenFeignClient;

    /**
     * @MethodName checkAccess
     * @Param judgeToken
     * @Param authorizationHeader
     * @Description 检查访问权限
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @Override
    public void checkAccess(String judgeToken, String authorizationHeader, JudgeNodeRequestContext requestContext) {
        ValidateJudgeNodeTokenRequest request = new ValidateJudgeNodeTokenRequest();
        request.setJudgeToken(StrUtil.trimToNull(judgeToken));
        request.setBearerToken(JudgeNodeRequestContextUtils.extractBearerToken(authorizationHeader));
        fillSignatureContext(request, requestContext);

        Result<JudgeNodeTokenValidationVo> result = judgeNodeTokenFeignClient.validate(request);
        if (result == null || result.getCode() != ResultCode.SUCCESS || result.getData() == null
                || !Boolean.TRUE.equals(result.getData().getValid())) {
            log.warn("Judge node token validation failed");
            throw new BizException(ResultCode.FORBIDDEN, "判题节点凭证无效");
        }
    }

    /**
     * @MethodName hasActiveNodeForMode
     * @Param judgeMode
     * @Description 具有用于模式活动节点
     * @Return @return boolean
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
    @Override
    public boolean hasActiveNodeForMode(String judgeMode) {
        String normalizedJudgeMode = StrUtil.blankToDefault(judgeMode, "default");
        try {
            Result<Boolean> result = judgeNodeTokenFeignClient.hasActiveNodeForMode(normalizedJudgeMode);
            return result != null && result.getCode() == ResultCode.SUCCESS && Boolean.TRUE.equals(result.getData());
        } catch (Exception e) {
            log.warn("Query judge node capability failed, judgeMode: {}", normalizedJudgeMode, e);
            return false;
        }
    }

    /**
     * @MethodName fillSignatureContext
     * @Param request
     * @Param requestContext
     * @Description 填充请求签名上下文
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/06/09
     */
    private void fillSignatureContext(ValidateJudgeNodeTokenRequest request, JudgeNodeRequestContext requestContext) {
        if (requestContext == null) {
            return;
        }
        request.setMethod(requestContext.getMethod());
        request.setPathWithQuery(requestContext.getPathWithQuery());
        request.setSourceIp(requestContext.getSourceIp());
        request.setNodeIdHeader(requestContext.getNodeIdHeader());
        request.setTokenIdHeader(requestContext.getTokenIdHeader());
        request.setInstanceId(requestContext.getInstanceId());
        request.setFingerprintHash(requestContext.getFingerprintHash());
        request.setSignatureAlgorithm(requestContext.getSignatureAlgorithm());
        request.setTimestamp(requestContext.getTimestamp());
        request.setNonce(requestContext.getNonce());
        request.setBodySha256(requestContext.getBodySha256());
        request.setActualBodySha256(requestContext.getActualBodySha256());
        request.setSignature(requestContext.getSignature());
    }
}
