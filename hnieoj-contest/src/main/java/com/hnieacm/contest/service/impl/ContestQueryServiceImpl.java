package com.hnieacm.contest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.contest.constant.ContestAuthConstant;
import com.hnieacm.contest.constant.ContestTypeConstant;
import com.hnieacm.contest.entity.Contest;
import com.hnieacm.contest.entity.ContestProblem;
import com.hnieacm.contest.mapper.ContestMapper;
import com.hnieacm.contest.mapper.ContestProblemMapper;
import com.hnieacm.contest.service.ContestQueryService;
import com.hnieacm.contest.vo.ContestDetailVo;
import com.hnieacm.contest.vo.ContestListVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 比赛查询服务实现
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
     * @Description 比赛列表
     * @Return @return {@link PageVo }<{@link ContestListVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    @Override
    public PageVo<ContestListVo> listContests(int page, int pageSize, String type, String auth) {
        ContestServiceSupport.validatePageParams(page, pageSize);

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

        return ContestServiceSupport.buildContestPageVo(
                contestMapper,
                contestProblemMapper,
                wrapper,
                page,
                pageSize,
                pageData -> {
                    Map<Long, Long> problemCountMap = pageData.problemCountMap();
                    return pageData.records().stream().map(contest -> {
                        ContestListVo vo = new ContestListVo();
                        vo.setId(contest.getId());
                        vo.setTitle(contest.getTitle());
                        vo.setType(ContestTypeConstant.toName(contest.getType()));
                        vo.setAuth(ContestAuthConstant.toName(contest.getAuth()));
                        vo.setSource(contest.getSource());
                        vo.setStartTime(contest.getStartTime());
                        vo.setEndTime(contest.getEndTime());
                        vo.setStatus(ContestServiceSupport.resolveRuntimeStatus(contest.getStartTime(), contest.getEndTime()));
                        vo.setProblemCount(Math.toIntExact(problemCountMap.getOrDefault(contest.getId(), 0L)));
                        vo.setCustomTags(ContestServiceSupport.parseCustomTags(objectMapper, log, contest.getCustomTags()));
                        return vo;
                    }).toList();
                });
    }

    /**
     * @MethodName getContestDetail
     * @Param contestId
     * @Description 获取比赛详情
     * @Return @return {@link ContestDetailVo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
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
            throw new BizException(ResultCode.NOT_FOUND, "比赛不存在或不可见");
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
        vo.setStatus(ContestServiceSupport.resolveRuntimeStatus(contest.getStartTime(), contest.getEndTime()));
        vo.setRankShowName(contest.getRankShowName());
        vo.setOpenRank(contest.getOpenRank() != null && contest.getOpenRank() == 1);
        vo.setSealRank(contest.getSealRank() != null && contest.getSealRank() == 1);
        vo.setSealRankTime(contest.getSealRankTime());
        vo.setProblemCount(problems.size());
        vo.setCustomTags(ContestServiceSupport.parseCustomTags(objectMapper, log, contest.getCustomTags()));
        vo.setProblems(problems.stream().map(ContestServiceSupport::toProblemVo).toList());
        vo.setGmtCreate(contest.getGmtCreate());
        vo.setGmtModified(contest.getGmtModified());
        return vo;
    }
}
