package com.hnieacm.training.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hnieacm.common.constant.HeaderConstant;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.training.constant.HomeworkStatusConstant;
import com.hnieacm.training.entity.Homework;
import com.hnieacm.training.entity.HomeworkProblem;
import com.hnieacm.training.mapper.HomeworkMapper;
import com.hnieacm.training.mapper.HomeworkProblemMapper;
import com.hnieacm.training.service.HomeworkClassAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * @author HnieOJ contributors
 */
@RestController
@RequestMapping("/internal/homeworks")
@RequiredArgsConstructor
public class InternalHomeworkAccessController {
    private final HomeworkMapper homeworkMapper;
    private final HomeworkProblemMapper problemMapper;
    private final HomeworkClassAccessService classAccess;

    @Value("${hnieoj.internal.token:}")
    private String internalToken;

    @GetMapping("/{homeworkId}/problems/{problemId}/access")
    public Result<Boolean> check(@PathVariable Long homeworkId, @PathVariable Long problemId,
                                 @RequestParam String uid,
                                 @RequestHeader(value = HeaderConstant.AUTHORIZATION, required = false) String authorization,
                                 @RequestHeader(value = HeaderConstant.INTERNAL_TOKEN, required = false) String token) {
        if (internalToken == null || internalToken.isBlank() || !internalToken.equals(token)) {
            throw new BizException(ResultCode.FORBIDDEN, "禁止访问内部接口");
        }
        Homework homework = homeworkMapper.selectById(homeworkId);
        LocalDateTime now = LocalDateTime.now();
        if (homework == null || !HomeworkStatusConstant.isEnabled(homework.getStatus())
                || now.isBefore(homework.getStartTime()) || now.isAfter(homework.getEndTime())) {
            throw new BizException(ResultCode.FORBIDDEN, "作业未开放或已截止");
        }
        if (problemMapper.selectCount(new LambdaQueryWrapper<HomeworkProblem>()
                .eq(HomeworkProblem::getHid, homeworkId)
                .eq(HomeworkProblem::getProblemId, problemId)) == 0) {
            throw new BizException(ResultCode.FORBIDDEN, "题目不属于该作业");
        }
        classAccess.requireAssigned(homeworkId, classAccess.currentClassId(uid, authorization));
        return Result.success(true);
    }
}
