package com.hnieacm.submission.controller;

import com.hnieacm.common.dto.ScoreSubmissionVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.submission.mapper.JudgeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.hnieacm.common.dto.HomeworkBestScoreVo;
import com.hnieacm.common.dto.HomeworkScoreQuery;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * @author HnieOJ contributors
 */
@RestController
@RequestMapping("/internal/submissions/scores")
@RequiredArgsConstructor
public class InternalScoreController {
    private final JudgeMapper judgeMapper;

    @GetMapping
    public Result<List<ScoreSubmissionVo>> list(@RequestParam String scope, @RequestParam Long id) {
        if (id == null || id <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "id 不合法");
        }
        if ("contest".equals(scope)) {
            return Result.success(judgeMapper.listContestScores(id));
        }
        if ("homework".equals(scope)) {
            return Result.success(judgeMapper.listHomeworkScores(id));
        }
        throw new BizException(ResultCode.BAD_REQUEST, "scope 不合法");
    }

    @PostMapping("/homework-best")
    public Result<List<HomeworkBestScoreVo>> homeworkBest(@RequestBody HomeworkScoreQuery query) {
        if (query == null || query.homeworkId() == null || query.homeworkId() <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "作业成绩查询参数不合法");
        }
        if (query.startTime() == null || query.endTime() == null
                || !query.endTime().isAfter(query.startTime())) {
            throw new BizException(ResultCode.BAD_REQUEST, "作业成绩查询参数不合法");
        }
        if (query.problemIds() == null || query.problemIds().isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "作业成绩查询参数不合法");
        }
        boolean invalidProblemIds = query.problemIds().stream().anyMatch(id -> id == null || id <= 0);
        if (invalidProblemIds) {
            throw new BizException(ResultCode.BAD_REQUEST, "作业成绩查询参数不合法");
        }
        return Result.success(judgeMapper.listHomeworkBestScores(query.homeworkId(), query.startTime(),
                query.endTime(), query.problemIds()));
    }
}
