package com.hnieacm.contest.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.result.Result;
import com.hnieacm.contest.service.ContestQueryService;
import com.hnieacm.contest.vo.ContestCheckVo;
import com.hnieacm.contest.vo.ContestDetailVo;
import com.hnieacm.contest.vo.ContestListVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @Operation(summary = "获取比赛列表")
    @GetMapping
    public Result<PageVo<ContestListVo>> list(
            @RequestParam @Min(value = 1, message = "page 必须大于等于 1") int page,
            @RequestParam @Min(value = 1, message = "pageSize 必须大于等于 1") int pageSize,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String auth) {
        return Result.success(contestQueryService.listContests(page, pageSize, type, auth));
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
