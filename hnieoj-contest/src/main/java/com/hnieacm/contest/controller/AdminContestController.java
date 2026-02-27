package com.hnieacm.contest.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.result.Result;
import com.hnieacm.contest.dto.AdminContestSaveRequest;
import com.hnieacm.contest.dto.AdminContestStatusRequest;
import com.hnieacm.contest.dto.AdminContestTeamSaveRequest;
import com.hnieacm.contest.dto.BatchDeleteContestTeamRequest;
import com.hnieacm.contest.service.ContestAdminService;
import com.hnieacm.contest.service.ContestTeamAdminService;
import com.hnieacm.contest.vo.AdminContestDetailVo;
import com.hnieacm.contest.vo.AdminContestListVo;
import com.hnieacm.contest.vo.AdminContestTeamListVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/27
 * @Description: 比赛管理模块（管理员）
 */
@Tag(name = "比赛管理模块(管理员)")
@Validated
@RestController
@RequestMapping("/api/admin/contest")
@RequiredArgsConstructor
@SaCheckRole(value = {RoleConstant.ADMIN, RoleConstant.ROOT}, mode = SaMode.OR)
public class AdminContestController {

    private final ContestAdminService contestAdminService;
    private final ContestTeamAdminService contestTeamAdminService;

    @Operation(summary = "获取比赛列表(Admin)")
    @GetMapping("/list")
    public Result<PageVo<AdminContestListVo>> list(@RequestParam @Min(value = 1, message = "page 必须大于等于 1") int page,
                                                    @RequestParam("pageSize") @Min(value = 1, message = "pageSize 必须大于等于 1") int pageSize,
                                                    @RequestParam(required = false) String keyword) {
        return Result.success(contestAdminService.listContests(page, pageSize, keyword));
    }

    @Operation(summary = "添加比赛")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody AdminContestSaveRequest request) {
        contestAdminService.createContest(request);
        return Result.success("创建成功", null);
    }

    @Operation(summary = "编辑比赛")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") @Min(value = 1, message = "id 必须大于等于 1") Long contestId,
                               @Valid @RequestBody AdminContestSaveRequest request) {
        contestAdminService.updateContest(contestId, request);
        return Result.success("更新成功", null);
    }

    @Operation(summary = "删除比赛")
    @DeleteMapping
    public Result<Void> delete(@RequestParam("id") @Min(value = 1, message = "id 必须大于等于 1") Long contestId) {
        contestAdminService.deleteContest(contestId);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "切换比赛状态")
    @PutMapping("/status")
    public Result<Void> changeStatus(@Valid @RequestBody AdminContestStatusRequest request) {
        contestAdminService.changeContestStatus(request);
        return Result.success("状态更新成功", null);
    }

    @Operation(summary = "获取比赛详情(Admin)")
    @GetMapping("/{id}")
    public Result<AdminContestDetailVo> detail(@PathVariable("id") @Min(value = 1, message = "id 必须大于等于 1") Long contestId) {
        return Result.success(contestAdminService.getContestDetail(contestId));
    }

    @Operation(summary = "获取比赛队伍列表")
    @GetMapping("/team")
    public Result<PageVo<AdminContestTeamListVo>> listTeams(@RequestParam("cid") @Min(value = 1, message = "cid 必须大于等于 1") Long contestId,
                                                             @RequestParam @Min(value = 1, message = "page 必须大于等于 1") int page,
                                                             @RequestParam("pageSize") @Min(value = 1, message = "pageSize 必须大于等于 1") int pageSize) {
        return Result.success(contestTeamAdminService.listTeams(contestId, page, pageSize));
    }

    @Operation(summary = "添加比赛队伍")
    @PostMapping("/team")
    public Result<Void> createTeam(@Valid @RequestBody AdminContestTeamSaveRequest request) {
        contestTeamAdminService.createTeam(request);
        return Result.success("添加队伍成功", null);
    }

    @Operation(summary = "更新比赛队伍")
    @PutMapping("/team/{id}")
    public Result<Void> updateTeam(@PathVariable("id") @Min(value = 1, message = "id 必须大于等于 1") Long teamId,
                                   @Valid @RequestBody AdminContestTeamSaveRequest request) {
        contestTeamAdminService.updateTeam(teamId, request);
        return Result.success("更新队伍成功", null);
    }

    @Operation(summary = "删除比赛队伍")
    @DeleteMapping("/team/{id}")
    public Result<Void> deleteTeam(@PathVariable("id") @Min(value = 1, message = "id 必须大于等于 1") Long teamId) {
        contestTeamAdminService.deleteTeam(teamId);
        return Result.success("删除队伍成功", null);
    }

    @Operation(summary = "批量删除比赛队伍")
    @DeleteMapping("/team")
    public Result<Void> batchDeleteTeam(@Valid @RequestBody BatchDeleteContestTeamRequest request) {
        contestTeamAdminService.batchDeleteTeam(request);
        return Result.success("批量删除成功", null);
    }
}
