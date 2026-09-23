package com.hnieacm.contest.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hnieacm.common.result.Result;
import com.hnieacm.contest.entity.Contest;
import com.hnieacm.contest.entity.ContestTeam;
import com.hnieacm.contest.entity.ContestTeamMember;
import com.hnieacm.contest.mapper.ContestMapper;
import com.hnieacm.contest.mapper.ContestTeamMapper;
import com.hnieacm.contest.mapper.ContestTeamMemberMapper;
import com.hnieacm.contest.vo.MyContestTeamVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * @author HnieOJ contributors
 */
@RestController
@SaCheckLogin
@RequestMapping("/api/contests/teams")
@RequiredArgsConstructor
public class MyContestTeamController {
    private final ContestTeamMemberMapper memberMapper;
    private final ContestTeamMapper teamMapper;
    private final ContestMapper contestMapper;

    @GetMapping("/mine")
    public Result<List<MyContestTeamVo>> mine() {
        List<ContestTeamMember> memberships = memberMapper.selectList(new LambdaQueryWrapper<ContestTeamMember>()
                .eq(ContestTeamMember::getUid, StpUtil.getLoginIdAsString()));
        List<MyContestTeamVo> rows = new ArrayList<>();
        for (ContestTeamMember membership : memberships) {
            ContestTeam team = teamMapper.selectById(membership.getTeamId());
            if (team == null) {
                continue;
            }
            Contest contest = contestMapper.selectById(team.getCid());
            if (contest == null || !Integer.valueOf(1).equals(contest.getIsVisible())) {
                continue;
            }
            MyContestTeamVo row = new MyContestTeamVo();
            row.setTeamId(team.getId());
            row.setTeamName(team.getName());
            row.setContestId(contest.getId());
            row.setContestTitle(contest.getTitle());
            row.setCaptainUid(team.getCaptainUid());
            rows.add(row);
        }
        return Result.success(rows);
    }
}
