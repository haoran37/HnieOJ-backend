package com.hnieacm.contest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.contest.constant.ContestAuthConstant;
import com.hnieacm.contest.constant.ContestRuntimeStatusConstant;
import com.hnieacm.contest.constant.ContestTypeConstant;
import com.hnieacm.contest.entity.Contest;
import com.hnieacm.contest.entity.ContestProblem;
import com.hnieacm.contest.mapper.ContestMapper;
import com.hnieacm.contest.mapper.ContestProblemMapper;
import com.hnieacm.contest.service.ContestQueryService;
import com.hnieacm.contest.vo.ContestDetailVo;
import com.hnieacm.contest.vo.ContestListVo;
import com.hnieacm.contest.vo.ContestProblemVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 竞赛查询服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContestQueryServiceImpl implements ContestQueryService {

    private static final int VISIBLE = 1;

    private final ContestMapper contestMapper;
    private final ContestProblemMapper contestProblemMapper;
    private final ObjectMapper objectMapper;

    /**
     * @MethodName listContests
     * @Param page
     * @Param pageSize
     * @Param type
     * @Param auth
     * @Description 竞赛列表
     * @Return @return {@link PageVo }<{@link ContestListVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/23
     */
    @Override
    public PageVo<ContestListVo> listContests(int page, int pageSize, String type, String auth) {
        if (page <= 0 || pageSize <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "page 和 pageSize 必须大于 0");
        }

        Integer contestType = ContestTypeConstant.fromName(type);
        Integer contestAuth = ContestAuthConstant.fromName(auth);

        LambdaQueryWrapper<Contest> wrapper = new LambdaQueryWrapper<Contest>()
                .eq(Contest::getIsVisible, VISIBLE)
                .orderByDesc(Contest::getStartTime)
                .orderByDesc(Contest::getId);

        if (contestType != null) {
            wrapper.eq(Contest::getType, contestType);
        }
        if (contestAuth != null) {
            wrapper.eq(Contest::getAuth, contestAuth);
        }

        Page<Contest> pageParam = new Page<>(page, pageSize);
        Page<Contest> pageResult = contestMapper.selectPage(pageParam, wrapper);
        List<Contest> records = pageResult.getRecords();
        if (records == null || records.isEmpty()) {
            return new PageVo<>(Collections.emptyList(), pageResult.getTotal());
        }

        Map<Long, Long> problemCountMap = queryProblemCountMap(records);
        List<ContestListVo> list = records.stream().map(contest -> {
            ContestListVo vo = new ContestListVo();
            vo.setId(contest.getId());
            vo.setTitle(contest.getTitle());
            vo.setType(ContestTypeConstant.toName(contest.getType()));
            vo.setAuth(ContestAuthConstant.toName(contest.getAuth()));
            vo.setSource(contest.getSource());
            vo.setStartTime(contest.getStartTime());
            vo.setEndTime(contest.getEndTime());
            vo.setStatus(resolveRuntimeStatus(contest.getStartTime(), contest.getEndTime()));
            vo.setProblemCount(Math.toIntExact(problemCountMap.getOrDefault(contest.getId(), 0L)));
            vo.setCustomTags(parseCustomTags(contest.getCustomTags()));
            return vo;
        }).toList();

        return new PageVo<>(list, pageResult.getTotal());
    }

    /**
     * @MethodName getContestDetail
     * @Param contestId
     * @Description 获取比赛详情
     * @Return @return {@link ContestDetailVo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/23
     */
    @Override
    public ContestDetailVo getContestDetail(Long contestId) {
        if (contestId == null || contestId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "contestId 不合法");
        }

        Contest contest = contestMapper.selectOne(new LambdaQueryWrapper<Contest>()
                .eq(Contest::getId, contestId)
                .eq(Contest::getIsVisible, VISIBLE)
                .last("limit 1"));
        if (contest == null) {
            throw new BizException(ResultCode.NOT_FOUND, "竞赛不存在或不可见");
        }

        List<ContestProblem> problems = contestProblemMapper.selectList(new LambdaQueryWrapper<ContestProblem>()
                .eq(ContestProblem::getCid, contestId)
                .orderByAsc(ContestProblem::getDisplayId)
                .orderByAsc(ContestProblem::getId));

        ContestDetailVo vo = new ContestDetailVo();
        vo.setId(contest.getId());
        vo.setUid(contest.getUid());
        vo.setAuthor(contest.getAuthor());
        vo.setTitle(contest.getTitle());
        vo.setDescription(contest.getDescription());
        vo.setStartTime(contest.getStartTime());
        vo.setEndTime(contest.getEndTime());
        vo.setType(ContestTypeConstant.toName(contest.getType()));
        vo.setAuth(ContestAuthConstant.toName(contest.getAuth()));
        vo.setSource(contest.getSource());
        vo.setStatus(resolveRuntimeStatus(contest.getStartTime(), contest.getEndTime()));
        vo.setRankShowName(contest.getRankShowName());
        vo.setOpenRank(contest.getOpenRank() != null && contest.getOpenRank() == 1);
        vo.setSealRank(contest.getSealRank() != null && contest.getSealRank() == 1);
        vo.setSealRankTime(contest.getSealRankTime());
        vo.setProblemCount(problems.size());
        vo.setCustomTags(parseCustomTags(contest.getCustomTags()));
        vo.setProblems(problems.stream().map(this::toProblemVo).toList());
        vo.setGmtCreate(contest.getGmtCreate());
        vo.setGmtModified(contest.getGmtModified());
        return vo;
    }

    /**
     * @MethodName queryProblemCountMap
     * @Param contests
     * @Description 查询竞赛题目数量
     * @Return @return {@link Map }<{@link Long }, {@link Long }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/23
     */
    private Map<Long, Long> queryProblemCountMap(List<Contest> contests) {
        List<Long> contestIds = contests.stream()
                .map(Contest::getId)
                .toList();
        if (contestIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<ContestProblem> relations = contestProblemMapper.selectList(new LambdaQueryWrapper<ContestProblem>()
                .select(ContestProblem::getCid)
                .in(ContestProblem::getCid, contestIds));
        if (relations == null || relations.isEmpty()) {
            return Collections.emptyMap();
        }
        return relations.stream().collect(Collectors.groupingBy(ContestProblem::getCid, Collectors.counting()));
    }

    /**
     * @MethodName toProblemVo
     * @Param problem
     * @Description 竞赛题目 VO
     * @Return @return {@link ContestProblemVo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/23
     */
    private ContestProblemVo toProblemVo(ContestProblem problem) {
        ContestProblemVo vo = new ContestProblemVo();
        vo.setId(problem.getId());
        vo.setProblemId(problem.getProblemId());
        vo.setDisplayId(problem.getDisplayId());
        vo.setDisplayTitle(problem.getDisplayTitle());
        vo.setColor(problem.getColor());
        return vo;
    }

    /**
     * @MethodName parseCustomTags
     * @Param json
     * @Description 解析自定义标签
     * @Return @return {@link List }<{@link String }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/23
     */
    private List<String> parseCustomTags(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<String> tags = objectMapper.readValue(json, new TypeReference<>() {
            });
            if (tags == null || tags.isEmpty()) {
                return Collections.emptyList();
            }
            return tags.stream()
                    .map(tag -> tag == null ? null : tag.trim())
                    .filter(tag -> tag != null && !tag.isEmpty())
                    .toList();
        } catch (Exception e) {
            log.warn("Parse contest custom tags failed, contest tags json: {}", json, e);
            return Collections.emptyList();
        }
    }

    /**
     * @MethodName resolveRuntimeStatus
     * @Param startTime
     * @Param endTime
     * @Description 解析运行时状态
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/02/23
     */
    private String resolveRuntimeStatus(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime now = LocalDateTime.now();
        if (startTime != null && now.isBefore(startTime)) {
            return ContestRuntimeStatusConstant.UPCOMING;
        }
        if (endTime != null && now.isAfter(endTime)) {
            return ContestRuntimeStatusConstant.ENDED;
        }
        return ContestRuntimeStatusConstant.RUNNING;
    }
}
