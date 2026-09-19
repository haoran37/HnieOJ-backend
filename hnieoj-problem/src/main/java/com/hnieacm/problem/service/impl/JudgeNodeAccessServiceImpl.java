package com.hnieacm.problem.service.impl;

import com.hnieacm.common.dto.JudgeTaskAccessRequest;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.problem.feign.JudgeNodeTokenFeignClient;
import com.hnieacm.problem.service.JudgeNodeAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 判题节点访问校验实现：problem 只做原始上下文透传，权威校验在 submission 同事务内完成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeNodeAccessServiceImpl implements JudgeNodeAccessService {

    private final JudgeNodeTokenFeignClient judgeNodeTokenFeignClient;

    @Override
    public void checkAccess(JudgeTaskAccessRequest request) {
        Result<Void> result = judgeNodeTokenFeignClient.validateTaskAccess(request);
        if (result == null || result.getCode() != ResultCode.SUCCESS) {
            log.warn("Judge node task access validation failed");
            throw new BizException(ResultCode.FORBIDDEN, "判题节点凭证无效");
        }
    }
}
