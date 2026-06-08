package com.hnieacm.submission.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.util.StrUtil;
import com.hnieacm.common.constant.PermissionConstant;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.submission.dto.CreateRejudgeTaskRequest;
import com.hnieacm.submission.dto.LegacyCreateRejudgeTaskRequest;
import com.hnieacm.submission.dto.RejudgeTaskQueryRequest;
import com.hnieacm.submission.service.RejudgeTaskService;
import com.hnieacm.submission.vo.RejudgeTaskDetailVo;
import com.hnieacm.submission.vo.RejudgeTaskVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/08
 * @Description: Legacy admin rejudge API.
 */
@Tag(name = "Admin Rejudge")
@Validated
@SaCheckLogin
@RestController
@RequestMapping("/api/admin/rejudge")
@RequiredArgsConstructor
public class AdminRejudgeController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RejudgeTaskService rejudgeTaskService;

    @Operation(summary = "Query rejudge tasks")
    @SaCheckPermission(PermissionConstant.PROBLEM_UPDATE)
    @GetMapping("/list")
    public Result<PageVo<RejudgeTaskVo>> list(@Valid RejudgeTaskQueryRequest request) {
        return Result.success(rejudgeTaskService.list(request));
    }

    @Operation(summary = "Create rejudge task")
    @SaCheckPermission(PermissionConstant.PROBLEM_UPDATE)
    @PostMapping
    public Result<RejudgeTaskVo> create(@RequestBody LegacyCreateRejudgeTaskRequest request) {
        return Result.success("重判任务已添加", rejudgeTaskService.create(toCreateRequest(request)));
    }

    @Operation(summary = "Query rejudge task details")
    @SaCheckPermission(PermissionConstant.PROBLEM_UPDATE)
    @GetMapping("/{taskId}/details")
    public Result<List<RejudgeTaskDetailVo>> details(@PathVariable Long taskId) {
        return Result.success(rejudgeTaskService.details(taskId));
    }

    private CreateRejudgeTaskRequest toCreateRequest(LegacyCreateRejudgeTaskRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        CreateRejudgeTaskRequest target = new CreateRejudgeTaskRequest();
        target.setProblemCode(resolveProblemCode(request));
        target.setContestId(request.getContestId());
        target.setRangeStart(request.getRangeStart());
        target.setRangeEnd(request.getRangeEnd());
        applyRange(target, request.getRange());
        return target;
    }

    private String resolveProblemCode(LegacyCreateRejudgeTaskRequest request) {
        String problemCode = StrUtil.trimToNull(request.getProblemCode());
        if (problemCode != null) {
            return problemCode;
        }
        problemCode = StrUtil.trimToNull(request.getProblemId());
        if (problemCode == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "problemId 不能为空");
        }
        return problemCode;
    }

    private void applyRange(CreateRejudgeTaskRequest target, Object range) {
        if (range == null) {
            return;
        }
        if (range instanceof String rangeText) {
            String normalized = StrUtil.trimToEmpty(rangeText);
            if (normalized.isEmpty() || "all".equalsIgnoreCase(normalized)) {
                target.setRangeStart(null);
                target.setRangeEnd(null);
                return;
            }
            throw new BizException(ResultCode.BAD_REQUEST, "range 只支持 all 或 {start,end}");
        }
        if (range instanceof Map<?, ?> rangeMap) {
            target.setRangeStart(parseRangeTime(rangeMap.get("start"), "range.start"));
            target.setRangeEnd(parseRangeTime(rangeMap.get("end"), "range.end"));
            return;
        }
        throw new BizException(ResultCode.BAD_REQUEST, "range 只支持 all 或 {start,end}");
    }

    private LocalDateTime parseRangeTime(Object value, String fieldName) {
        if (value == null) {
            return null;
        }
        String text = StrUtil.trimToNull(String.valueOf(value));
        if (text == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(text, DATE_TIME_FORMATTER);
            } catch (DateTimeParseException e) {
                throw new BizException(ResultCode.BAD_REQUEST, fieldName + " 时间格式不正确");
            }
        }
    }
}
