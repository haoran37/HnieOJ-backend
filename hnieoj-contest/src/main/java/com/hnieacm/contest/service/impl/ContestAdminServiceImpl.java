package com.hnieacm.contest.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.contest.constant.ContestAuthConstant;
import com.hnieacm.contest.constant.ContestRegisterTypeConstant;
import com.hnieacm.contest.constant.ContestRuntimeStatusConstant;
import com.hnieacm.contest.constant.ContestTypeConstant;
import com.hnieacm.contest.dto.AdminContestProblemRequest;
import com.hnieacm.contest.dto.AdminContestSaveRequest;
import com.hnieacm.contest.dto.AdminContestStatusRequest;
import com.hnieacm.contest.entity.Contest;
import com.hnieacm.contest.entity.ContestProblem;
import com.hnieacm.contest.entity.ContestRegister;
import com.hnieacm.contest.entity.ContestTeam;
import com.hnieacm.contest.entity.ContestTeamMember;
import com.hnieacm.contest.mapper.ContestMapper;
import com.hnieacm.contest.mapper.ContestProblemMapper;
import com.hnieacm.contest.mapper.ContestRegisterMapper;
import com.hnieacm.contest.mapper.ContestTeamMapper;
import com.hnieacm.contest.mapper.ContestTeamMemberMapper;
import com.hnieacm.contest.service.ContestAdminService;
import com.hnieacm.contest.service.manager.ContestProblemManager;
import com.hnieacm.contest.service.manager.ContestUserManager;
import com.hnieacm.contest.vo.AdminContestAccountVo;
import com.hnieacm.contest.vo.AdminContestDetailVo;
import com.hnieacm.contest.vo.AdminContestListVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/27
 * @Description: 比赛管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContestAdminServiceImpl implements ContestAdminService {

    private static final int VISIBLE = 1;
    private static final int HIDDEN = 0;
    private static final int REGISTER_SUCCESS = 1;
    private static final int STATUS_UPCOMING = -1;
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_ENDED = 1;
    private static final String DEFAULT_RANK_SHOW_NAME = "username";

    private final ContestMapper contestMapper;
    private final ContestProblemMapper contestProblemMapper;
    private final ContestRegisterMapper contestRegisterMapper;
    private final ContestTeamMapper contestTeamMapper;
    private final ContestTeamMemberMapper contestTeamMemberMapper;
    private final ContestProblemManager contestProblemManager;
    private final ContestUserManager contestUserManager;
    private final ObjectMapper objectMapper;

    /**
     * @MethodName listContests
     * @Param page
     * @Param page
     * @Param page
     * @Description 比赛列表
     * @Return @return {@link PageVo }<{@link AdminContestListVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @Override
    public PageVo<AdminContestListVo> listContests(int page, int pageSize, String keyword) {
        ContestServiceSupport.validatePageParams(page, pageSize);

        LambdaQueryWrapper<Contest> wrapper = new LambdaQueryWrapper<Contest>()
                .orderByDesc(Contest::getId);
        if (StrUtil.isNotBlank(keyword)) {
            String normalizedKeyword = keyword.trim();
            wrapper.and(w -> w.like(Contest::getTitle, normalizedKeyword)
                    .or()
                    .like(Contest::getSource, normalizedKeyword)
                    .or()
                    .like(Contest::getAuthor, normalizedKeyword));
        }

        return ContestServiceSupport.buildContestPageVo(
                contestMapper,
                contestProblemMapper,
                wrapper,
                page,
                pageSize,
                pageData -> {
                    Map<Long, Long> problemCountMap = pageData.problemCountMap();
                    return pageData.records().stream().map(contest -> {
                        AdminContestListVo vo = new AdminContestListVo();
                        vo.setId(contest.getId());
                        vo.setTitle(contest.getTitle());
                        vo.setType(ContestTypeConstant.toName(contest.getType()));
                        vo.setAuth(ContestAuthConstant.toName(contest.getAuth()));
                        vo.setPermission(ContestAuthConstant.toName(contest.getAuth()));
                        vo.setSource(contest.getSource());
                        vo.setAuthor(contest.getAuthor());
                        vo.setStartTime(contest.getStartTime());
                        vo.setEndTime(contest.getEndTime());
                        vo.setStatus(contest.getIsVisible() != null && contest.getIsVisible() == VISIBLE);
                        vo.setRuntimeStatus(ContestServiceSupport.resolveRuntimeStatus(contest.getStartTime(), contest.getEndTime()));
                        vo.setProblemCount(Math.toIntExact(problemCountMap.getOrDefault(contest.getId(), 0L)));
                        vo.setCustomTags(ContestServiceSupport.parseCustomTags(objectMapper, log, contest.getCustomTags()));
                        return vo;
                    }).toList();
                });
    }

    /**
     * @MethodName createContest
     * @Param request
     * @Description 创建比赛
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createContest(AdminContestSaveRequest request) {
        validateContestSaveRequest(request);

        Contest contest = buildContestEntity(null, request);
        contestMapper.insert(contest);

        replaceContestProblems(contest.getId(), request.getProblems());
        replaceContestAccounts(contest.getId(), resolveAccountList(contest.getAuth(), request.getAccountList()));
    }

    /**
     * @MethodName updateContest
     * @Param contestId
     * @Param contestId
     * @Description 更新比赛
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateContest(Long contestId, AdminContestSaveRequest request) {
        Contest existedContest = getContestById(contestId);
        validateContestSaveRequest(request);

        Contest contest = buildContestEntity(existedContest, request);
        contest.setId(contestId);
        contestMapper.updateById(contest);

        replaceContestProblems(contestId, request.getProblems());
        replaceContestAccounts(contestId, resolveAccountList(contest.getAuth(), request.getAccountList()));
    }

    /**
     * @MethodName deleteContest
     * @Param contestId
     * @Description 删除比赛
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteContest(Long contestId) {
        getContestById(contestId);
        contestMapper.deleteById(contestId);

        contestProblemMapper.delete(new LambdaQueryWrapper<ContestProblem>()
                .eq(ContestProblem::getCid, contestId));
        contestRegisterMapper.delete(new LambdaQueryWrapper<ContestRegister>()
                .eq(ContestRegister::getCid, contestId));

        List<ContestTeam> teams = contestTeamMapper.selectList(new LambdaQueryWrapper<ContestTeam>()
                .select(ContestTeam::getId)
                .eq(ContestTeam::getCid, contestId));
        if (teams != null && !teams.isEmpty()) {
            List<Long> teamIds = teams.stream().map(ContestTeam::getId).toList();
            contestTeamMemberMapper.delete(new LambdaQueryWrapper<ContestTeamMember>()
                    .in(ContestTeamMember::getTeamId, teamIds));
        }
        contestTeamMapper.delete(new LambdaQueryWrapper<ContestTeam>()
                .eq(ContestTeam::getCid, contestId));
    }

    /**
     * @MethodName changeContestStatus
     * @Param request
     * @Description 更改比赛状态
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @Override
    public void changeContestStatus(AdminContestStatusRequest request) {
        Contest contest = getContestById(request.getId());
        contest.setIsVisible(Boolean.TRUE.equals(request.getStatus()) ? VISIBLE : HIDDEN);
        contestMapper.updateById(contest);
    }

    /**
     * @MethodName getContestDetail
     * @Param contestId
     * @Description 获取比赛详情
     * @Return @return {@link AdminContestDetailVo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @Override
    public AdminContestDetailVo getContestDetail(Long contestId) {
        Contest contest = getContestById(contestId);

        List<ContestProblem> problems = contestProblemMapper.selectList(new LambdaQueryWrapper<ContestProblem>()
                .eq(ContestProblem::getCid, contestId)
                .orderByAsc(ContestProblem::getDisplayId)
                .orderByAsc(ContestProblem::getId));

        List<ContestRegister> registers = contestRegisterMapper.selectList(new LambdaQueryWrapper<ContestRegister>()
                .eq(ContestRegister::getCid, contestId)
                .eq(ContestRegister::getType, ContestRegisterTypeConstant.USER)
                .orderByAsc(ContestRegister::getId));

        List<String> accountUids = registers.stream()
                .map(ContestRegister::getUid)
                .filter(Objects::nonNull)
                .toList();
        Map<String, String> usernameMap = contestUserManager.queryUsernameMap(accountUids);
        List<AdminContestAccountVo> accountList = registers.stream().map(register -> {
            AdminContestAccountVo accountVo = new AdminContestAccountVo();
            accountVo.setUid(register.getUid());
            accountVo.setUsername(usernameMap.getOrDefault(register.getUid(), register.getUid()));
            return accountVo;
        }).toList();

        AdminContestDetailVo vo = new AdminContestDetailVo();
        vo.setId(contest.getId());
        vo.setUid(contest.getUid());
        vo.setAuthor(contest.getAuthor());
        vo.setTitle(contest.getTitle());
        vo.setDescription(contest.getDescription());
        vo.setType(ContestTypeConstant.toName(contest.getType()));
        vo.setAuth(ContestAuthConstant.toName(contest.getAuth()));
        vo.setPermission(ContestAuthConstant.toName(contest.getAuth()));
        vo.setSource(contest.getSource());
        vo.setStatus(contest.getIsVisible() != null && contest.getIsVisible() == VISIBLE);
        vo.setStartTime(contest.getStartTime());
        vo.setEndTime(contest.getEndTime());
        vo.setTimeRange(buildTimeRange(contest.getStartTime(), contest.getEndTime()));
        vo.setRuntimeStatus(ContestServiceSupport.resolveRuntimeStatus(contest.getStartTime(), contest.getEndTime()));
        vo.setRankShowName(contest.getRankShowName());
        vo.setOpenRank(contest.getOpenRank() != null && contest.getOpenRank() == VISIBLE);
        vo.setSealRank(contest.getSealRank() != null && contest.getSealRank() == VISIBLE);
        vo.setSealRankTime(contest.getSealRankTime());
        vo.setCustomTags(ContestServiceSupport.parseCustomTags(objectMapper, log, contest.getCustomTags()));
        vo.setProblems(problems.stream().map(ContestServiceSupport::toProblemVo).toList());
        vo.setAccountList(accountList);
        vo.setGmtCreate(contest.getGmtCreate());
        vo.setGmtModified(contest.getGmtModified());
        return vo;
    }

    /**
     * @MethodName buildContestEntity
     * @Param existedContest
     * @Param existedContest
     * @Description 建立比赛实体
     * @Return @return {@link Contest }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private Contest buildContestEntity(Contest existedContest, AdminContestSaveRequest request) {
        LocalDateTime startTime = ContestServiceSupport.toLocalDateTime(request.getStartTime());
        LocalDateTime endTime = ContestServiceSupport.toLocalDateTime(request.getEndTime());
        if (startTime == null || endTime == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "startTime/endTime 不合法");
        }
        if (!endTime.isAfter(startTime)) {
            throw new BizException(ResultCode.BAD_REQUEST, "endTime 必须晚于 startTime");
        }

        Integer contestType = ContestTypeConstant.fromName(request.getType());
        Integer contestAuth = ContestAuthConstant.fromName(request.getAuth());
        if (contestType == null || contestAuth == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "比赛参数不合法");
        }

        Contest contest = new Contest();
        if (existedContest == null) {
            String operatorUid = StpUtil.getLoginIdAsString();
            contest.setUid(operatorUid);
            contest.setAuthor(operatorUid);
            contest.setPwd(null);
        } else {
            contest.setUid(existedContest.getUid());
            contest.setAuthor(existedContest.getAuthor());
            contest.setGmtCreate(existedContest.getGmtCreate());
            contest.setPwd(existedContest.getPwd());
        }

        contest.setTitle(request.getTitle().trim());
        contest.setType(contestType);
        contest.setAuth(contestAuth);
        contest.setSource(StrUtil.blankToDefault(StrUtil.trim(request.getSource()), null));
        contest.setDescription(StrUtil.blankToDefault(StrUtil.trim(request.getDescription()), null));
        contest.setStartTime(startTime);
        contest.setEndTime(endTime);
        contest.setIsVisible(Boolean.TRUE.equals(request.getStatus()) ? VISIBLE : HIDDEN);
        contest.setStatus(resolveDbStatus(startTime, endTime));
        contest.setRankShowName(resolveRankShowName(request.getRankShowName(), existedContest));
        contest.setOpenRank(resolveFlagValue(request.getOpenRank(), existedContest == null ? null : existedContest.getOpenRank(), VISIBLE));
        contest.setSealRank(resolveFlagValue(request.getSealRank(), existedContest == null ? null : existedContest.getSealRank(), HIDDEN));
        contest.setSealRankTime(resolveSealRankTime(request.getSealRankTime(), existedContest));
        contest.setCustomTags(writeCustomTags(request.getCustomTags()));
        return contest;
    }

    /**
     * @MethodName validateContestSaveRequest
     * @Param request
     * @Description 验证比赛保存请求
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private void validateContestSaveRequest(AdminContestSaveRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        if (request.getStartTime() == null || request.getEndTime() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "startTime 和 endTime 不能为空");
        }
    }

    /**
     * @MethodName replaceContestProblems
     * @Param contestId
     * @Param contestId
     * @Description 统一替换比赛题目，避免残留脏数据
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private void replaceContestProblems(Long contestId, List<AdminContestProblemRequest> problems) {
        contestProblemMapper.delete(new LambdaQueryWrapper<ContestProblem>().eq(ContestProblem::getCid, contestId));
        List<AdminContestProblemRequest> normalizedProblems = normalizeProblems(problems);
        if (normalizedProblems.isEmpty()) {
            return;
        }
        List<Long> problemIds = normalizedProblems.stream()
                .map(AdminContestProblemRequest::getProblemId)
                .toList();
        Map<Long, String> problemTitleMap = contestProblemManager.queryProblemTitleMap(problemIds);
        for (Long problemId : problemIds) {
            if (!problemTitleMap.containsKey(problemId)) {
                throw new BizException(ResultCode.PROBLEM_NOT_FOUND, "题目不存在: " + problemId);
            }
        }

        Set<String> usedDisplayIds = new LinkedHashSet<>();
        int autoDisplayIdIndex = 0;
        for (AdminContestProblemRequest problem : normalizedProblems) {
            String requestDisplayId = StrUtil.trim(problem.getDisplayId());
            String displayId;
            if (StrUtil.isNotBlank(requestDisplayId)) {
                displayId = requestDisplayId;
                if (usedDisplayIds.contains(displayId.toUpperCase())) {
                    throw new BizException(ResultCode.BAD_REQUEST, "displayId 不能重复: " + displayId);
                }
            } else {
                do {
                    displayId = buildDisplayIdByIndex(autoDisplayIdIndex);
                    autoDisplayIdIndex++;
                } while (usedDisplayIds.contains(displayId.toUpperCase()));
            }
            usedDisplayIds.add(displayId.toUpperCase());

            ContestProblem contestProblem = new ContestProblem();
            contestProblem.setCid(contestId);
            contestProblem.setProblemId(problem.getProblemId());
            contestProblem.setDisplayId(displayId);
            contestProblem.setDisplayTitle(resolveDisplayTitle(problem, problemTitleMap));
            contestProblem.setColor(StrUtil.blankToDefault(StrUtil.trim(problem.getColor()), null));
            contestProblemMapper.insert(contestProblem);
        }
    }

    /**
     * @MethodName replaceContestAccounts
     * @Param contestId
     * @Param contestId
     * @Description 统一替换限定账号，确保数据库与请求内容一致
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private void replaceContestAccounts(Long contestId, List<String> accountList) {
        contestRegisterMapper.delete(new LambdaQueryWrapper<ContestRegister>()
                .eq(ContestRegister::getCid, contestId)
                .eq(ContestRegister::getType, ContestRegisterTypeConstant.USER));

        List<String> normalizedUids = contestUserManager.normalizeUids(accountList);
        if (normalizedUids.isEmpty()) {
            return;
        }
        contestUserManager.ensureUsersExist(normalizedUids);

        for (String uid : normalizedUids) {
            ContestRegister register = new ContestRegister();
            register.setCid(contestId);
            register.setUid(uid);
            register.setStatus(REGISTER_SUCCESS);
            register.setType(ContestRegisterTypeConstant.USER);
            contestRegisterMapper.insert(register);
        }
    }

    /**
     * @MethodName normalizeProblems
     * @Param problems
     * @Description 标准化题目
     * @Return @return {@link List }<{@link AdminContestProblemRequest }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private List<AdminContestProblemRequest> normalizeProblems(List<AdminContestProblemRequest> problems) {
        if (problems == null || problems.isEmpty()) {
            return Collections.emptyList();
        }
        List<AdminContestProblemRequest> result = new ArrayList<>();
        Set<Long> problemIdSet = new LinkedHashSet<>();
        for (AdminContestProblemRequest problem : problems) {
            if (problem == null) {
                continue;
            }
            Long problemId = problem.getProblemId();
            if (problemId == null || problemId <= 0) {
                throw new BizException(ResultCode.BAD_REQUEST, "problemId 不能为空且必须大于 0");
            }
            if (!problemIdSet.add(problemId)) {
                throw new BizException(ResultCode.BAD_REQUEST, "problemId 不能重复: " + problemId);
            }
            result.add(problem);
        }
        return result;
    }

    /**
     * @MethodName resolveDisplayTitle
     * @Param problem
     * @Param problem
     * @Description 解析显示标题
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private String resolveDisplayTitle(AdminContestProblemRequest problem, Map<Long, String> problemTitleMap) {
        String requestDisplayTitle = StrUtil.trim(problem.getDisplayTitle());
        if (StrUtil.isNotBlank(requestDisplayTitle)) {
            return requestDisplayTitle;
        }
        String title = problemTitleMap.get(problem.getProblemId());
        if (StrUtil.isBlank(title)) {
            throw new BizException(ResultCode.PROBLEM_NOT_FOUND, "题目不存在: " + problem.getProblemId());
        }
        return title;
    }

    /**
     * @MethodName buildDisplayIdByIndex
     * @Param index
     * @Description 根据索引构造 displayId（0->A, 25->Z, 26->AA）
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private String buildDisplayIdByIndex(int index) {
        if (index < 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "displayId 索引不合法");
        }
        int value = index + 1;
        StringBuilder builder = new StringBuilder();
        while (value > 0) {
            int remainder = (value - 1) % 26;
            builder.append((char) ('A' + remainder));
            value = (value - 1) / 26;
        }
        return builder.reverse().toString();
    }

    /**
     * @MethodName resolveAccountList
     * @Param contestAuth
     * @Param contestAuth
     * @Description 解析帐户列表
     * @Return @return {@link List }<{@link String }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private List<String> resolveAccountList(Integer contestAuth, List<String> accountList) {
        if (contestAuth == null || contestAuth != ContestAuthConstant.PRIVATE) {
            return Collections.emptyList();
        }
        return accountList;
    }

    /**
     * @MethodName getContestById
     * @Param contestId
     * @Description 通过 id 获取比赛
     * @Return @return {@link Contest }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private Contest getContestById(Long contestId) {
        return ContestServiceSupport.getContestById(contestMapper, contestId, "比赛不存在");
    }

    /**
     * @MethodName writeCustomTags
     * @Param customTags
     * @Description 编写自定义标签
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private String writeCustomTags(List<String> customTags) {
        if (customTags == null || customTags.isEmpty()) {
            return null;
        }
        List<String> normalizedTags = customTags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), ArrayList::new));
        if (normalizedTags.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(normalizedTags);
        } catch (Exception e) {
            log.error("Write contest custom tags failed, tags: {}", normalizedTags, e);
            throw new BizException(ResultCode.BAD_REQUEST, "customTags 参数不合法");
        }
    }

    /**
     * @MethodName buildTimeRange
     * @Param startTime
     * @Param startTime
     * @Description 构建时间范围
     * @Return @return {@link List }<{@link Long }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private List<Long> buildTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            return Collections.emptyList();
        }
        return List.of(ContestServiceSupport.toEpochMilli(startTime), ContestServiceSupport.toEpochMilli(endTime));
    }

    /**
     * @MethodName resolveDbStatus
     * @Param startTime
     * @Param startTime
     * @Description 解析数据库状态
     * @Return @return {@link Integer }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private Integer resolveDbStatus(LocalDateTime startTime, LocalDateTime endTime) {
        String runtimeStatus = ContestServiceSupport.resolveRuntimeStatus(startTime, endTime);
        if (ContestRuntimeStatusConstant.UPCOMING.equals(runtimeStatus)) {
            return STATUS_UPCOMING;
        }
        if (ContestRuntimeStatusConstant.ENDED.equals(runtimeStatus)) {
            return STATUS_ENDED;
        }
        return STATUS_RUNNING;
    }

    /**
     * @MethodName resolveRankShowName
     * @Param requestValue
     * @Param requestValue
     * @Description 解析排名显示名称
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private String resolveRankShowName(String requestValue, Contest existedContest) {
        String rankShowName = StrUtil.trim(requestValue);
        if (StrUtil.isNotBlank(rankShowName)) {
            return rankShowName;
        }
        if (existedContest != null && StrUtil.isNotBlank(existedContest.getRankShowName())) {
            return existedContest.getRankShowName();
        }
        return DEFAULT_RANK_SHOW_NAME;
    }

    /**
     * @MethodName resolveFlagValue
     * @Param requestValue
     * @Param requestValue
     * @Param requestValue
     * @Description 解析标志值
     * @Return @return {@link Integer }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private Integer resolveFlagValue(Boolean requestValue, Integer existedValue, int defaultValue) {
        if (requestValue != null) {
            return requestValue ? VISIBLE : HIDDEN;
        }
        if (existedValue != null) {
            return existedValue;
        }
        return defaultValue;
    }

    /**
     * @MethodName resolveSealRankTime
     * @Param requestValue
     * @Param requestValue
     * @Description 解析封榜时间
     * @Return @return {@link LocalDateTime }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    private LocalDateTime resolveSealRankTime(Long requestValue, Contest existedContest) {
        if (requestValue != null) {
            return ContestServiceSupport.toLocalDateTime(requestValue);
        }
        if (existedContest != null) {
            return existedContest.getSealRankTime();
        }
        return null;
    }
}
