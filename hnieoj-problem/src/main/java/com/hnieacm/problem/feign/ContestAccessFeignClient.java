package com.hnieacm.problem.feign;

import com.hnieacm.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * @Author: HnieOJ contributors
 * @Description: Internal contest access lookup shared by problem and submission services.
 */
@FeignClient(name = "hnieoj-contest", contextId = "contestAccessFeignClient")
public interface ContestAccessFeignClient {

    /**
     * Check that a user may access an active contest problem.
     *
     * @param contestId contest ID
     * @param problemId internal problem ID
     * @param uid participant UID
     * @return access result
     */
    @GetMapping("/internal/contests/{contestId}/problems/{problemId}/access")
    Result<Boolean> checkProblemAccess(@PathVariable("contestId") Long contestId,
                                       @PathVariable("problemId") Long problemId,
                                       @RequestParam("uid") String uid);
}
