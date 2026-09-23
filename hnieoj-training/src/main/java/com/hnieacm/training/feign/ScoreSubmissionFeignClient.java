package com.hnieacm.training.feign;

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
     * List judged submissions for a homework.
     * @param scope score scope
     * @param id homework identifier
     * @return judged submissions
     */
    @GetMapping("/internal/submissions/scores")
    Result<List<ScoreSubmissionVo>> listScores(@RequestParam("scope") String scope,
                                               @RequestParam("id") Long id);

    /**
     * Get per-user, per-problem scores already aggregated by the submission database.
     * @param query homework window and problem scope
     * @return best scores
     */
    @org.springframework.web.bind.annotation.PostMapping("/internal/submissions/scores/homework-best")
    Result<List<com.hnieacm.common.dto.HomeworkBestScoreVo>> listHomeworkBestScores(
            @org.springframework.web.bind.annotation.RequestBody com.hnieacm.common.dto.HomeworkScoreQuery query);
}
