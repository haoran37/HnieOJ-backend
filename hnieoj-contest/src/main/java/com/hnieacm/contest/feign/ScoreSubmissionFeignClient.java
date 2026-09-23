package com.hnieacm.contest.feign;

import com.hnieacm.common.dto.ScoreSubmissionVo;
import com.hnieacm.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * @author HnieOJ contributors
 */
@FeignClient(name = "hnieoj-submission", contextId = "scoreSubmissionFeignClient")
public interface ScoreSubmissionFeignClient {
    /**
     * List submissions used to calculate standings.
     * @param scope contest or homework
     * @param id contest or homework identifier
     * @return judged submissions
     */
    @GetMapping("/internal/submissions/scores")
    Result<List<ScoreSubmissionVo>> listScores(@RequestParam("scope") String scope,
                                               @RequestParam("id") Long id);
}
