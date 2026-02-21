package com.hnieacm.submission.feign;

import com.hnieacm.common.result.Result;
import com.hnieacm.submission.dto.ProblemBasicDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 题目服务的内部 Feign 客户端
 */
@FeignClient(name = "hnieoj-problem")
public interface ProblemInternalFeignClient {

    @GetMapping("/internal/problems/{problemCode}")
    Result<ProblemBasicDto> getProblemBasic(@PathVariable String problemCode);
}
