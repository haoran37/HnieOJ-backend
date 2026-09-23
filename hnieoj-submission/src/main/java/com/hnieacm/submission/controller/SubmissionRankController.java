package com.hnieacm.submission.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.hnieacm.common.result.Result;
import com.hnieacm.submission.mapper.JudgeMapper;
import com.hnieacm.submission.vo.UserSolveRankVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

/**
 * @author HnieOJ contributors
 */
@RestController
@SaCheckLogin
@RequestMapping("/api/submissions/rankings")
@RequiredArgsConstructor
public class SubmissionRankController {
    private final JudgeMapper judgeMapper;

    @GetMapping
    public Result<List<UserSolveRankVo>> list(@RequestParam(defaultValue = "all") String period) {
        var monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        List<UserSolveRankVo> rows;
        if ("month".equals(period)) {
            rows = judgeMapper.listMonthlySolveRanks(monthStart);
        } else if ("all".equals(period)) {
            rows = judgeMapper.listSolveRanks(monthStart);
        } else {
            throw new com.hnieacm.common.exception.BizException(com.hnieacm.common.result.ResultCode.BAD_REQUEST,
                    "period 不合法");
        }
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setRank(i + 1);
        }
        return Result.success(rows);
    }
}
