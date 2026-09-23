package com.hnieacm.training.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.hnieacm.common.constant.HeaderConstant;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.result.Result;
import com.hnieacm.training.service.HomeworkQueryService;
import com.hnieacm.training.service.HomeworkClassAccessService;
import com.hnieacm.training.service.HomeworkRankService;
import com.hnieacm.training.vo.HomeworkDetailVo;
import com.hnieacm.training.vo.HomeworkListVo;
import com.hnieacm.training.vo.HomeworkRankVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 作业模块对外接口
 */
@Tag(name = "作业模块")
@Validated
@RestController
@RequestMapping("/api/homeworks")
@RequiredArgsConstructor
@SaCheckRole(
        value = {
                RoleConstant.STUDENT,
                RoleConstant.TA,
                RoleConstant.TEACHER,
                RoleConstant.ADMIN,
                RoleConstant.ROOT
        },
        mode = SaMode.OR
)
public class HomeworkController {

    private final HomeworkQueryService homeworkQueryService;
    private final HomeworkRankService homeworkRankService;
    private final HomeworkClassAccessService classAccess;

    private boolean isManager() {
        return StpUtil.hasRole(RoleConstant.ADMIN) || StpUtil.hasRole(RoleConstant.ROOT)
                || StpUtil.hasRole(RoleConstant.TEACHER) || StpUtil.hasRole(RoleConstant.TA);
    }

    @Operation(summary = "作业成绩单")
    @GetMapping("/{id}/rankings")
    public Result<List<HomeworkRankVo>> rankings(@PathVariable("id") @Min(1) Long homeworkId,
            @RequestHeader(HeaderConstant.AUTHORIZATION) String authorization) {
        if (!isManager()) {
            classAccess.requireAssigned(homeworkId,
                    classAccess.currentClassId(StpUtil.getLoginIdAsString(), authorization));
        }
        return Result.success(homeworkRankService.standings(homeworkId));
    }

    @Operation(summary = "获取作业列表")
    @GetMapping
    public Result<PageVo<HomeworkListVo>> list(
            @RequestParam @Min(value = 1, message = "page 必须大于等于 1") int page,
            @RequestParam @Min(value = 1, message = "pageSize 必须大于等于 1") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) List<Long> classIds,
            @RequestHeader(HeaderConstant.AUTHORIZATION) String authorization) {
        if (!isManager()) {
            classId = classAccess.currentClassId(StpUtil.getLoginIdAsString(), authorization);
            classIds = null;
        }
        return Result.success(homeworkQueryService.listHomeworks(page, pageSize, keyword, classId, classIds));
    }

    @Operation(summary = "获取作业详情")
    @GetMapping("/{id}")
    public Result<HomeworkDetailVo> detail(@PathVariable("id") @Min(value = 1, message = "id 必须大于等于 1") Long homeworkId,
            @RequestHeader(HeaderConstant.AUTHORIZATION) String authorization) {
        if (!isManager()) {
            classAccess.requireAssigned(homeworkId,
                    classAccess.currentClassId(StpUtil.getLoginIdAsString(), authorization));
        }
        return Result.success(homeworkQueryService.getHomeworkDetail(homeworkId));
    }
}
