package com.hnieacm.contest.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.contest.constant.ContestAuthConstant;
import com.hnieacm.contest.constant.ContestListWindowConstant;
import com.hnieacm.contest.constant.ContestTypeConstant;
import com.hnieacm.contest.dto.ContestListQuery;
import com.hnieacm.contest.entity.Contest;
import com.hnieacm.contest.entity.ContestProblem;
import com.hnieacm.contest.entity.ContestRegister;
import com.hnieacm.contest.mapper.ContestMapper;
import com.hnieacm.contest.mapper.ContestProblemMapper;
import com.hnieacm.contest.mapper.ContestRegisterMapper;
import com.hnieacm.contest.service.ContestQueryService;
import com.hnieacm.contest.vo.ContestCheckVo;
import com.hnieacm.contest.vo.ContestDetailVo;
import com.hnieacm.contest.vo.ContestListVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
    private final ContestRegisterMapper contestRegisterMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void checkProblemAccess(Long contestId, Long problemId, String uid) {
        if (contestId == null || contestId <= 0 || problemId == null || problemId <= 0 || StrUtil.isBlank(uid)) {
            throw new BizException(ResultCode.BAD_REQUEST, "比赛题目访问参数不合法");
        }
        Contest contest = contestMapper.selectById(contestId);
        LocalDateTime now = LocalDateTime.now();
        if (contest == null || contest.getIsVisible() == null || contest.getIsVisible() != VISIBLE
                || contest.getStartTime() == null || contest.getEndTime() == null
                || now.isBefore(contest.getStartTime()) || !now.isBefore(contest.getEndTime())) {
            throw new BizException(ResultCode.FORBIDDEN, "比赛未开放或已结束");
        }
        if (contestProblemMapper.selectCount(new LambdaQueryWrapper<ContestProblem>()
                .eq(ContestProblem::getCid, contestId)
                .eq(ContestProblem::getProblemId, problemId)) == 0) {
            throw new BizException(ResultCode.FORBIDDEN, "题目不属于该比赛");
        }
        if (contestRegisterMapper.selectCount(new LambdaQueryWrapper<ContestRegister>()
                .eq(ContestRegister::getCid, contestId)
                .eq(ContestRegister::getUid, uid)
                .eq(ContestRegister::getStatus, 1)) == 0) {
            throw new BizException(ResultCode.FORBIDDEN, "未获得该比赛的参赛资格");
        }
    }

    /**
     * @MethodName listContests
     * @Param query
     * @Description 比赛列表（type/auth 过滤 + 开始时间窗过滤 + window=recent 排序）
     * @Return @return {@link PageVo }<{@link ContestListVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/09/21
     */
    @Override
    public PageVo<ContestListVo> listContests(ContestListQuery query) {
        return listContests(query, null);
    }

    @Override
    public PageVo<ContestListVo> listContests(ContestListQuery query, String participantUid) {
        int page = query.page();
        int pageSize = query.pageSize();
        ContestServiceSupport.validatePageParams(page, pageSize);

        Integer contestType = ContestTypeConstant.fromName(query.type());
        Integer contestAuth = ContestAuthConstant.fromName(query.auth());
        String window = ContestListWindowConstant.normalize(query.window());
        Long startFrom = query.startFrom();
        Long startTo = query.startTo();
        if (startFrom != null && startTo != null && startFrom > startTo) {
            throw new BizException(ResultCode.BAD_REQUEST, "startFrom 不能晚于 startTo");
        }

        LambdaQueryWrapper<Contest> wrapper = new LambdaQueryWrapper<Contest>()
                .eq(Contest::getIsVisible, VISIBLE);

        if (participantUid != null && !participantUid.isBlank()) {
            List<Long> registeredIds = contestRegisterMapper.selectList(new LambdaQueryWrapper<ContestRegister>()
                    .eq(ContestRegister::getUid, participantUid.trim())
                    .eq(ContestRegister::getStatus, 1)).stream()
                    .map(ContestRegister::getCid).distinct().toList();
            if (registeredIds.isEmpty()) {
                return new PageVo<>(List.of(), 0L);
            }
            wrapper.in(Contest::getId, registeredIds);
        }

        if (ContestListWindowConstant.RECENT.equals(window)) {
            // 「距当前由近到远」：不能用 orderByDesc(startTime)，否则永远拿到开始时间最晚的那场
            // （可能是很久以后的未来比赛，与「近期比赛」语义不符）。pivot 用 JVM 时钟，
            // 与同一个响应里 resolveRuntimeStatus 算出的 status 共用一套时间。
            wrapper.last(ContestServiceSupport.recentOrderBySql(LocalDateTime.now()));
        } else {
            wrapper.orderByDesc(Contest::getStartTime).orderByDesc(Contest::getId);
        }

        if (contestType != null) {
            wrapper.eq(Contest::getType, contestType);
        }
        if (contestAuth != null) {
            wrapper.eq(Contest::getAuth, contestAuth);
        }
        if (startFrom != null) {
            wrapper.ge(Contest::getStartTime, ContestServiceSupport.toLocalDateTime(startFrom));
        }
        if (startTo != null) {
            wrapper.le(Contest::getStartTime, ContestServiceSupport.toLocalDateTime(startTo));
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

    @Override
    public ContestCheckVo checkContestExists(Long contestId) {
        if (contestId == null || contestId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "contestId 不合法");
        }

        Contest contest = contestMapper.selectOne(new LambdaQueryWrapper<Contest>()
                .eq(Contest::getId, contestId)
                .eq(Contest::getIsVisible, VISIBLE)
                .last("limit 1"));

        ContestCheckVo vo = new ContestCheckVo();
        vo.setContestId(contestId);
        vo.setValid(contest != null);
        vo.setTitle(contest == null ? null : contest.getTitle());
        return vo;
    }
}
