package com.hnieacm.submission.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.judge.dto.ValidateJudgeNodeTokenRequest;
import com.hnieacm.judge.service.JudgeNodeHeartbeatService;
import com.hnieacm.judge.service.JudgeNodeSecurityService;
import com.hnieacm.judge.vo.JudgeNodeIdentity;
import com.hnieacm.judge.vo.JudgeNodeTokenValidationVo;
import com.hnieacm.submission.service.JudgeNodeAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 判题节点访问校验服务实现（合并判题域后改为本地调用）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeNodeAccessServiceImpl implements JudgeNodeAccessService {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String DEFAULT_JUDGE_MODE = "default";

    private final JudgeNodeSecurityService judgeNodeSecurityService;
    private final JudgeNodeHeartbeatService judgeNodeHeartbeatService;

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
    public void checkAccess(String judgeToken, String authorizationHeader) {
        ValidateJudgeNodeTokenRequest request = new ValidateJudgeNodeTokenRequest();
        request.setJudgeToken(StrUtil.trimToNull(judgeToken));
        request.setBearerToken(extractBearerToken(authorizationHeader));

        JudgeNodeTokenValidationVo validation = judgeNodeSecurityService.validateToken(request);
        if (validation == null || !Boolean.TRUE.equals(validation.getValid())) {
            log.warn("Judge node token validation failed");
            throw new BizException(ResultCode.FORBIDDEN, "判题节点凭证无效");
        }
    }

    @Override
    public JudgeNodeIdentity resolveIdentity(String judgeToken, String authorizationHeader) {
        return judgeNodeSecurityService.resolveIdentity(judgeToken, extractAuthorization(authorizationHeader));
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
        String normalizedJudgeMode = StrUtil.blankToDefault(judgeMode, DEFAULT_JUDGE_MODE);
        try {
            return judgeNodeHeartbeatService.hasActiveNodeForMode(normalizedJudgeMode);
        } catch (Exception e) {
            log.warn("Query judge node capability failed, judgeMode: {}", normalizedJudgeMode, e);
            return false;
        }
    }

    private String extractBearerToken(String authorizationHeader) {
        String normalizedHeader = StrUtil.trimToNull(authorizationHeader);
        if (normalizedHeader == null) {
            return null;
        }
        if (normalizedHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return StrUtil.trimToNull(normalizedHeader.substring(BEARER_PREFIX.length()));
        }
        return null;
    }

    private String extractAuthorization(String authorizationHeader) {
        return StrUtil.trimToNull(authorizationHeader);
    }
}
