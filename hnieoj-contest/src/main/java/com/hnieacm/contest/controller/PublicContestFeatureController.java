package com.hnieacm.contest.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hnieacm.common.result.Result;
import com.hnieacm.contest.constant.ContestAuthConstant;
import com.hnieacm.contest.constant.ContestTypeConstant;
import com.hnieacm.contest.entity.Contest;
import com.hnieacm.contest.mapper.ContestMapper;
import com.hnieacm.contest.vo.FeaturedContestVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * @author HnieOJ contributors
 */
@RestController
@RequestMapping("/api/contests/featured")
@RequiredArgsConstructor
public class PublicContestFeatureController {
    private final ContestMapper contestMapper;

    @GetMapping
    public Result<FeaturedContestVo> featured() {
        LocalDateTime now = LocalDateTime.now();
        Contest contest = contestMapper.selectOne(new LambdaQueryWrapper<Contest>()
                .eq(Contest::getIsVisible, 1).eq(Contest::getAuth, ContestAuthConstant.PUBLIC)
                .le(Contest::getStartTime, now).gt(Contest::getEndTime, now)
                .orderByDesc(Contest::getStartTime).last("limit 1"));
        String status = "running";
        if (contest == null) {
            contest = contestMapper.selectOne(new LambdaQueryWrapper<Contest>()
                    .eq(Contest::getIsVisible, 1).eq(Contest::getAuth, ContestAuthConstant.PUBLIC)
                    .gt(Contest::getStartTime, now).orderByAsc(Contest::getStartTime).last("limit 1"));
            status = "upcoming";
        }
        if (contest == null) {
            return Result.success(null);
        }
        FeaturedContestVo result = new FeaturedContestVo();
        result.setId(contest.getId());
        result.setTitle(contest.getTitle());
        result.setType(ContestTypeConstant.toName(contest.getType()));
        result.setStartTime(contest.getStartTime());
        result.setEndTime(contest.getEndTime());
        result.setStatus(status);
        return Result.success(result);
    }
}
