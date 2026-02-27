package com.hnieacm.contest.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.contest.dto.AdminContestTeamSaveRequest;
import com.hnieacm.contest.dto.BatchDeleteContestTeamRequest;
import com.hnieacm.contest.entity.ContestTeam;
import com.hnieacm.contest.entity.ContestTeamMember;
import com.hnieacm.contest.mapper.ContestMapper;
import com.hnieacm.contest.mapper.ContestTeamMapper;
import com.hnieacm.contest.mapper.ContestTeamMemberMapper;
import com.hnieacm.contest.service.ContestTeamAdminService;
import com.hnieacm.contest.service.manager.ContestUserManager;
import com.hnieacm.contest.vo.AdminContestTeamListVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/27
 * @Description: 比赛队伍管理服务实现
 */
@Service
@RequiredArgsConstructor
public class ContestTeamAdminServiceImpl implements ContestTeamAdminService {

    private static final int CAPTAIN = 1;
    private static final int NON_CAPTAIN = 0;

    private final ContestMapper contestMapper;
    private final ContestTeamMapper contestTeamMapper;
    private final ContestTeamMemberMapper contestTeamMemberMapper;
    private final ContestUserManager contestUserManager;

    /**
     * @MethodName listTeams
     * @Param contestId
     * @Param page
     * @Param pageSize
     * @Description 比赛团队列表
     * @Return @return {@link PageVo }<{@link AdminContestTeamListVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @Override
    public PageVo<AdminContestTeamListVo> listTeams(Long contestId, int page, int pageSize) {
        if (contestId == null || contestId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "cid 必须大于 0");
        }
        ContestServiceSupport.validatePageParams(page, pageSize);
        ensureContestExist(contestId);

        Page<ContestTeam> pageParam = new Page<>(page, pageSize);
        Page<ContestTeam> teamPage = contestTeamMapper.selectPage(pageParam, new LambdaQueryWrapper<ContestTeam>()
                .eq(ContestTeam::getCid, contestId)
                .orderByDesc(ContestTeam::getId));
        List<ContestTeam> teams = teamPage.getRecords();
        if (teams == null || teams.isEmpty()) {
            return new PageVo<>(Collections.emptyList(), teamPage.getTotal());
        }

        List<Long> teamIds = teams.stream().map(ContestTeam::getId).toList();
        List<ContestTeamMember> members = contestTeamMemberMapper.selectList(new LambdaQueryWrapper<ContestTeamMember>()
                .in(ContestTeamMember::getTeamId, teamIds)
                .orderByAsc(ContestTeamMember::getIsCaptain)
                .orderByAsc(ContestTeamMember::getId));

        Map<Long, List<ContestTeamMember>> memberMap = members.stream()
                .collect(Collectors.groupingBy(ContestTeamMember::getTeamId));
        Map<String, String> userNameMap = contestUserManager.queryUsernameMap(members.stream()
                .map(ContestTeamMember::getUid)
                .filter(Objects::nonNull)
                .toList());

        List<AdminContestTeamListVo> list = teams.stream().map(team -> buildTeamListVo(team, memberMap.get(team.getId()), userNameMap))
                .toList();
        return new PageVo<>(list, teamPage.getTotal());
    }

    /**
     * @MethodName createTeam
     * @Param request
     * @Description 创建比赛团队
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createTeam(AdminContestTeamSaveRequest request) {
        Long contestId = request.getCid();
        if (contestId == null || contestId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "cid 必须大于 0");
        }
        ensureContestExist(contestId);

        List<String> memberUids = normalizeMemberUids(request);
        contestUserManager.ensureUsersExist(memberUids);

        ContestTeam team = new ContestTeam();
        team.setCid(contestId);
        team.setName(request.getName().trim());
        team.setCaptainUid(memberUids.get(0));
        contestTeamMapper.insert(team);
        replaceTeamMembers(team.getId(), memberUids);
    }

    /**
     * @MethodName updateTeam
     * @Param teamId
     * @Param request
     * @Description 更新比赛团队
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTeam(Long teamId, AdminContestTeamSaveRequest request) {
        ContestTeam existedTeam = getTeamById(teamId);
        if (request.getCid() != null && !request.getCid().equals(existedTeam.getCid())) {
            throw new BizException(ResultCode.BAD_REQUEST, "不支持变更队伍所属比赛");
        }

        List<String> memberUids = normalizeMemberUids(request);
        contestUserManager.ensureUsersExist(memberUids);

        existedTeam.setName(request.getName().trim());
        existedTeam.setCaptainUid(memberUids.get(0));
        contestTeamMapper.updateById(existedTeam);
        replaceTeamMembers(teamId, memberUids);
    }

    /**
     * @MethodName deleteTeam
     * @Param teamId
     * @Description 删除比赛团队
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTeam(Long teamId) {
        getTeamById(teamId);
        contestTeamMemberMapper.delete(new LambdaQueryWrapper<ContestTeamMember>()
                .eq(ContestTeamMember::getTeamId, teamId));
        contestTeamMapper.deleteById(teamId);
    }

    /**
     * @MethodName batchDeleteTeam
     * @Param request
     * @Description 批量删除比赛团队
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteTeam(BatchDeleteContestTeamRequest request) {
        List<Long> ids = normalizeTeamIds(request.getIds());
        if (ids.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "ids 不能为空");
        }
        contestTeamMemberMapper.delete(new LambdaQueryWrapper<ContestTeamMember>()
                .in(ContestTeamMember::getTeamId, ids));
        contestTeamMapper.delete(new LambdaQueryWrapper<ContestTeam>()
                .in(ContestTeam::getId, ids));
    }

    /**
     * @MethodName buildTeamListVo
     * @Param team
     * @Param members
     * @Param userNameMap
     * @Description 构建比赛团队列表vo
     * @Return @return {@link AdminContestTeamListVo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private AdminContestTeamListVo buildTeamListVo(ContestTeam team,
                                                   List<ContestTeamMember> members,
                                                   Map<String, String> userNameMap) {
        List<ContestTeamMember> sortedMembers = sortMembers(members);

        AdminContestTeamListVo vo = new AdminContestTeamListVo();
        vo.setId(team.getId());
        vo.setCid(team.getCid());
        vo.setName(team.getName());
        vo.setCreateTime(team.getGmtCreate());
        fillMemberInfo(vo, sortedMembers, userNameMap);
        return vo;
    }

    /**
     * @MethodName sortMembers
     * @Param members
     * @Description 对队员排序
     * @Return @return {@link List }<{@link ContestTeamMember }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private List<ContestTeamMember> sortMembers(List<ContestTeamMember> members) {
        if (members == null || members.isEmpty()) {
            return Collections.emptyList();
        }
        return members.stream()
                .sorted(Comparator
                        .comparing((ContestTeamMember item) -> item.getIsCaptain() == null ? NON_CAPTAIN : -item.getIsCaptain())
                        .thenComparing(ContestTeamMember::getId))
                .toList();
    }

    /**
     * @MethodName fillMemberInfo
     * @Param vo
     * @Param members
     * @Param userNameMap
     * @Description 填写队员信息
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private void fillMemberInfo(AdminContestTeamListVo vo,
                                List<ContestTeamMember> members,
                                Map<String, String> userNameMap) {
        for (int index = 0; index < members.size() && index < 3; index++) {
            ContestTeamMember member = members.get(index);
            String uid = member.getUid();
            String username = userNameMap.getOrDefault(uid, uid);
            if (index == 0) {
                vo.setMember1Uid(uid);
                vo.setMember1Name(username);
            } else if (index == 1) {
                vo.setMember2Uid(uid);
                vo.setMember2Name(username);
            } else {
                vo.setMember3Uid(uid);
                vo.setMember3Name(username);
            }
        }
    }

    /**
     * @MethodName normalizeMemberUids
     * @Param request
     * @Description 规范化队员uid
     * @Return @return {@link List }<{@link String }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private List<String> normalizeMemberUids(AdminContestTeamSaveRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        String teamName = StrUtil.trim(request.getName());
        if (StrUtil.isBlank(teamName)) {
            throw new BizException(ResultCode.BAD_REQUEST, "name 不能为空");
        }
        request.setName(teamName);

        List<String> members = new ArrayList<>();
        String captainUid = StrUtil.trim(request.getMember1Uid());
        if (StrUtil.isBlank(captainUid)) {
            throw new BizException(ResultCode.BAD_REQUEST, "member1Uid 不能为空");
        }
        members.add(captainUid);

        String member2Uid = StrUtil.trim(request.getMember2Uid());
        if (StrUtil.isNotBlank(member2Uid)) {
            members.add(member2Uid);
        }
        String member3Uid = StrUtil.trim(request.getMember3Uid());
        if (StrUtil.isNotBlank(member3Uid)) {
            members.add(member3Uid);
        }

        Set<String> memberSet = new HashSet<>(members);
        if (memberSet.size() != members.size()) {
            throw new BizException(ResultCode.BAD_REQUEST, "队伍成员 uid 不能重复");
        }
        return members;
    }

    /**
     * @MethodName replaceTeamMembers
     * @Param teamId
     * @Param memberUids
     * @Description 替换比赛团队队员
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private void replaceTeamMembers(Long teamId, List<String> memberUids) {
        contestTeamMemberMapper.delete(new LambdaQueryWrapper<ContestTeamMember>()
                .eq(ContestTeamMember::getTeamId, teamId));
        for (int index = 0; index < memberUids.size(); index++) {
            ContestTeamMember member = new ContestTeamMember();
            member.setTeamId(teamId);
            member.setUid(memberUids.get(index));
            member.setIsCaptain(index == 0 ? CAPTAIN : NON_CAPTAIN);
            contestTeamMemberMapper.insert(member);
        }
    }

    /**
     * @MethodName normalizeTeamIds
     * @Param ids
     * @Description 规范化比赛团队id
     * @Return @return {@link List }<{@link Long }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private List<Long> normalizeTeamIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
    }

    /**
     * @MethodName getTeamById
     * @Param teamId
     * @Description 按id获取比赛团队
     * @Return @return {@link ContestTeam }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private ContestTeam getTeamById(Long teamId) {
        if (teamId == null || teamId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "teamId 不合法");
        }
        ContestTeam team = contestTeamMapper.selectById(teamId);
        if (team == null) {
            throw new BizException(ResultCode.NOT_FOUND, "比赛队伍不存在");
        }
        return team;
    }

    /**
     * @MethodName ensureContestExist
     * @Param contestId
     * @Description 确保比赛存在
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private void ensureContestExist(Long contestId) {
        ContestServiceSupport.getContestById(contestMapper, contestId, "比赛不存在");
    }
}
