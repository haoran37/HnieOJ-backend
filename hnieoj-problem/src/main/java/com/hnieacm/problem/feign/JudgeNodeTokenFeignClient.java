package com.hnieacm.problem.feign;

import com.hnieacm.common.constant.HeaderConstant;
import com.hnieacm.common.result.Result;
import com.hnieacm.problem.dto.JudgeTaskAccessRequest;
import com.hnieacm.problem.dto.ValidateJudgeNodeTokenRequest;
import com.hnieacm.problem.vo.JudgeNodeTokenValidationVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 判题服务节点 Token / 任务资格内部 API
 */
@FeignClient(name = "hnieoj-submission")
public interface JudgeNodeTokenFeignClient {

    @PostMapping("/internal/judge/tokens/validate")
    Result<JudgeNodeTokenValidationVo> validate(@RequestBody ValidateJudgeNodeTokenRequest request);

    @PostMapping("/internal/judge/tasks/access")
    Result<Boolean> validateTaskAccess(@RequestBody JudgeTaskAccessRequest request,
                                       @RequestHeader(HeaderConstant.AUTHORIZATION) String authorization,
                                       @RequestHeader(HeaderConstant.JUDGE_TOKEN) String judgeToken);
}
