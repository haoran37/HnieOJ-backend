package com.hnieacm.submission.feign;

import com.hnieacm.common.result.Result;
import com.hnieacm.submission.dto.UserDetailDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 用户公共 API 的 Feign 客户端
 */
@FeignClient(name = "hnieoj-user")
public interface UserProfileFeignClient {

    @GetMapping("/api/user/users/{uid}")
    Result<UserDetailDto> getUserDetail(@PathVariable String uid);
}
