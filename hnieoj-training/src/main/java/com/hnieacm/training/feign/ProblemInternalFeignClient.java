package com.hnieacm.training.feign;

import com.hnieacm.common.result.Result;
import com.hnieacm.training.feign.dto.ProblemBasicInfoDto;
import com.hnieacm.training.feign.dto.ProblemBatchQueryRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/28
 * @Description: 题目服务内部接口 Feign 客户端
 */
@FeignClient(name = "hnieoj-problem")
public interface ProblemInternalFeignClient {

    @PostMapping("/internal/problems/basic-info/by-ids")
    Result<List<ProblemBasicInfoDto>> queryProblemBasicByIds(@RequestBody ProblemBatchQueryRequest request);
}
