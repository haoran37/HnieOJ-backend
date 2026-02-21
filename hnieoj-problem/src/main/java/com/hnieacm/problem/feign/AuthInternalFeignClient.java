package com.hnieacm.problem.feign;

import com.hnieacm.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 认证服务内部 API
 */
@FeignClient(name = "hnieoj-auth")
public interface AuthInternalFeignClient {

    @PostMapping("/internal/auth/cache/refresh/{uid}")
    Result<Void> refreshUserAuthCache(@PathVariable String uid);
}

