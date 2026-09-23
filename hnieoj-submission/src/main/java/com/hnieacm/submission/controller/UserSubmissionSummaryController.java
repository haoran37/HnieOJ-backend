package com.hnieacm.submission.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.hnieacm.common.result.Result;
import com.hnieacm.submission.mapper.JudgeMapper;
import com.hnieacm.submission.vo.UserProblemSummaryVo;
import com.hnieacm.submission.vo.UserSubmissionSummaryVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Public OJ activity shown on user profile pages to any signed-in viewer.
 * This endpoint exposes only aggregates and problem results already visible in the submission list.
 *
 * @author HnieOJ contributors
 */
@RestController
@SaCheckLogin
@RequestMapping("/api/submissions/users")
@RequiredArgsConstructor
public class UserSubmissionSummaryController {
    private final JudgeMapper judgeMapper;

    @GetMapping("/{uid}/summary")
    public Result<UserSubmissionSummaryVo> summary(@PathVariable String uid) {
        if (uid == null || uid.isBlank() || uid.length() > 50) {
            throw new com.hnieacm.common.exception.BizException(com.hnieacm.common.result.ResultCode.BAD_REQUEST,
                    "uid 不合法");
        }
        List<UserProblemSummaryVo> problems = judgeMapper.listUserProblems(uid);
        UserSubmissionSummaryVo result = new UserSubmissionSummaryVo();
        result.setTotalSubmissions(judgeMapper.countUserSubmissions(uid));
        result.setAcceptedProblems(judgeMapper.countUserAcceptedProblems(uid));
        result.setProblems(problems);
        result.setDaily(judgeMapper.listUserDaily(uid, LocalDate.now().minusDays(364).atStartOfDay()));
        return Result.success(result);
    }
}
