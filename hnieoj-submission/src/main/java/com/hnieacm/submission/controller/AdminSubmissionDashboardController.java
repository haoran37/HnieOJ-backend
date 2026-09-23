package com.hnieacm.submission.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.hnieacm.common.constant.PermissionConstant;
import com.hnieacm.common.result.Result;
import com.hnieacm.submission.mapper.JudgeMapper;
import com.hnieacm.submission.vo.AdminSubmissionDashboardVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * @author HnieOJ contributors
 */
@RestController
@SaCheckLogin
@RequestMapping("/api/admin/submissions/dashboard")
@RequiredArgsConstructor
public class AdminSubmissionDashboardController {
    private final JudgeMapper judgeMapper;

    @GetMapping
    @SaCheckPermission(PermissionConstant.PROBLEM_UPDATE)
    public Result<AdminSubmissionDashboardVo> summary() {
        LocalDate reportDate = LocalDate.now();
        AdminSubmissionDashboardVo result = new AdminSubmissionDashboardVo();
        result.setReportDate(reportDate);
        result.setTotalSubmissions(judgeMapper.selectCount(null));
        result.setDaily(judgeMapper.listDashboardDaily(reportDate.minusDays(6).atStartOfDay()));
        result.setStatuses(judgeMapper.listDashboardStatuses());
        result.setHotProblems(judgeMapper.listHotProblems());
        result.setLowActivityProblems(judgeMapper.listLowActivityProblems());
        return Result.success(result);
    }
}
