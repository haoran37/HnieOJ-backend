package com.hnieacm.submission.feign;

import com.hnieacm.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * @author HnieOJ contributors
 */
@FeignClient(name = "hnieoj-training", contextId = "homeworkAccessFeignClient")
public interface HomeworkAccessFeignClient {
    /**
     * Check whether a homework accepts this problem submission.
     * @param homeworkId homework identifier
     * @param problemId problem identifier
     * @param uid submitting user's identifier
     * @return whether access is allowed
     */
    @GetMapping("/internal/homeworks/{homeworkId}/problems/{problemId}/access")
    Result<Boolean> checkAccess(@PathVariable("homeworkId") Long homeworkId,
                                @PathVariable("problemId") Long problemId,
                                @RequestParam("uid") String uid);
}
