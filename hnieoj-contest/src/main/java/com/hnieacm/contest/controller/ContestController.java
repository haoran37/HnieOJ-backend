package com.hnieacm.contest.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.result.Result;
import com.hnieacm.contest.dto.ContestListQuery;
import com.hnieacm.contest.service.ContestQueryService;
import com.hnieacm.contest.service.ContestRankService;
import com.hnieacm.contest.service.ContestRegistrationService;
import com.hnieacm.contest.vo.ContestRankVo;
import com.hnieacm.contest.vo.ContestRatingVo;
import com.hnieacm.contest.vo.ContestCheckVo;
import com.hnieacm.contest.vo.ContestDetailVo;
import com.hnieacm.contest.vo.ContestListVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 比赛模块对外接口
 */
@Tag(name = "比赛模块")
@Validated
@SaCheckLogin
@RestController
@RequestMapping("/api/contests")
@RequiredArgsConstructor
public class ContestController {

    private final ContestQueryService contestQueryService;
    private final ContestRankService contestRankService;
    private final ContestRegistrationService registrationService;

    @Operation(summary = "查看本人报名状态")
    @GetMapping("/{id}/registration")
    public Result<Boolean> registration(@PathVariable("id") @Min(1) Long contestId) {
        return Result.success(registrationService.isRegistered(contestId, StpUtil.getLoginIdAsString()));
    }

    @Operation(summary = "报名公开比赛")
    @PostMapping("/{id}/registration")
    public Result<Void> register(@PathVariable("id") @Min(1) Long contestId) {
        registrationService.register(contestId, StpUtil.getLoginIdAsString());
        return Result.success("报名成功", null);
    }

    @Operation(summary = "比赛评分排行榜")
    @GetMapping("/ratings")
    public Result<List<ContestRatingVo>> ratings() {
        return Result.success(contestRankService.ratings());
    }

    @Operation(summary = "用户比赛评分")
    @GetMapping("/ratings/{uid}")
    public Result<ContestRatingVo> rating(@PathVariable String uid) {
        return Result.success(contestRankService.ratings().stream()
                .filter(row -> row.getUid().equals(uid)).findFirst().orElse(null));
    }

    @Operation(summary = "比赛实时榜单")
    @GetMapping("/{id}/scoreboard")
    public Result<List<ContestRankVo>> scoreboard(@PathVariable("id") @Min(1) Long contestId) {
        boolean manager = StpUtil.hasRole(RoleConstant.ADMIN) || StpUtil.hasRole(RoleConstant.ROOT);
        return Result.success(contestRankService.standings(contestId, StpUtil.getLoginIdAsString(), manager));
    }

    @Operation(summary = "获取比赛列表")
    @GetMapping
    public Result<PageVo<ContestListVo>> list(
            @RequestParam @Min(value = 1, message = "page 必须大于等于 1") int page,
            @RequestParam @Min(value = 1, message = "pageSize 必须大于等于 1") int pageSize,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String auth,
            @RequestParam(required = false) @Min(value = 1, message = "startFrom 必须大于 0") Long startFrom,
            @RequestParam(required = false) @Min(value = 1, message = "startTo 必须大于 0") Long startTo,
            @RequestParam(required = false) String window,
            @RequestParam(required = false) String participantUid) {
        return Result.success(contestQueryService.listContests(
                new ContestListQuery(page, pageSize, type, auth, startFrom, startTo, window), participantUid));
    }

    @Operation(summary = "获取比赛详情")
    @GetMapping("/{id}")
    public Result<ContestDetailVo> detail(@PathVariable("id") @Min(value = 1, message = "id 必须大于等于 1") Long contestId) {
        return Result.success(contestQueryService.getContestDetail(contestId));
    }

    @Operation(summary = "检查比赛 ID 有效性")
    @GetMapping("/check")
    public Result<ContestCheckVo> check(@RequestParam("cid") @Min(value = 1, message = "cid 必须大于 0") Long contestId) {
        return Result.success(contestQueryService.checkContestExists(contestId));
    }
}
