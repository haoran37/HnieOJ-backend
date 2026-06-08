package com.hnieacm.problem.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.problem.dto.ValidateJudgeNodeTokenRequest;
import com.hnieacm.problem.feign.JudgeNodeTokenFeignClient;
import com.hnieacm.problem.service.JudgeNodeAccessService;
import com.hnieacm.problem.vo.JudgeNodeTokenValidationVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 判题节点访问校验服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeNodeAccessServiceImpl implements JudgeNodeAccessService {

    private static final String BEARER_PREFIX = "Bearer ";

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
    public void checkAccess(String judgeToken, String authorizationHeader) {
        ValidateJudgeNodeTokenRequest request = new ValidateJudgeNodeTokenRequest();
        request.setJudgeToken(StrUtil.trimToNull(judgeToken));
        request.setBearerToken(extractBearerToken(authorizationHeader));

        Result<JudgeNodeTokenValidationVo> result = judgeNodeTokenFeignClient.validate(request);
        if (result == null || result.getCode() != ResultCode.SUCCESS || result.getData() == null
                || !Boolean.TRUE.equals(result.getData().getValid())) {
            log.warn("Judge node token validation failed");
            throw new BizException(ResultCode.FORBIDDEN, "判题节点凭证无效");
        }
    }

    /**
     * @MethodName extractBearerToken
     * @Param authorizationHeader
     * @Description 提取 BearerToken
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/06/08
     */
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
}
