package com.hnieacm.contest.feign;

import com.hnieacm.common.result.Result;
import com.hnieacm.contest.feign.dto.UserBasicDto;
import com.hnieacm.contest.feign.dto.UserBatchQueryRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/27
 * @Description: 用户服务内部接口 Feign 客户端
 */
@FeignClient(name = "hnieoj-user")
public interface UserInternalFeignClient {

    @PostMapping("/internal/users/basic-info")
    Result<List<UserBasicDto>> queryUsersByUids(@RequestBody UserBatchQueryRequest request);
}
