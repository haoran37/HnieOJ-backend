package com.hnieacm.training.feign;

import com.hnieacm.common.constant.HeaderConstant;
import com.hnieacm.common.result.Result;
import com.hnieacm.training.vo.HomeworkUserVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Reads the submitting user's current class from the user service.
 * @author HnieOJ contributors
 */
@FeignClient(name = "hnieoj-user", contextId = "homeworkUserFeignClient")
public interface HomeworkUserFeignClient {
    /**
     * Get the user's public profile while preserving the caller's login token.
     * @param uid user identifier
     * @param authorization caller's authorization header
     * @return user profile fields needed for homework access
     */
    @GetMapping("/api/user/users/{uid}")
    Result<HomeworkUserVo> getUserDetail(@PathVariable("uid") String uid,
                                         @RequestHeader(HeaderConstant.AUTHORIZATION) String authorization);
}
