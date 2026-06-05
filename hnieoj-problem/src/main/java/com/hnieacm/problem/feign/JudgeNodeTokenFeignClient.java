package com.hnieacm.problem.feign;

import com.hnieacm.common.result.Result;
import com.hnieacm.problem.dto.ValidateJudgeNodeTokenRequest;
import com.hnieacm.problem.vo.JudgeNodeTokenValidationVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 判题服务节点 Token 内部 API
 */
@FeignClient(name = "hnieoj-judge")
public interface JudgeNodeTokenFeignClient {

    @PostMapping("/internal/judge/tokens/validate")
    Result<JudgeNodeTokenValidationVo> validate(@RequestBody ValidateJudgeNodeTokenRequest request);
}
