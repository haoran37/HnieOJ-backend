package com.hnieacm.contest.service;

import com.hnieacm.common.dto.PageVo;
import com.hnieacm.contest.dto.ContestListQuery;
import com.hnieacm.contest.vo.ContestCheckVo;
import com.hnieacm.contest.vo.ContestDetailVo;
import com.hnieacm.contest.vo.ContestListVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 比赛查询服务
 */
public interface ContestQueryService {

    /**
     * 比赛列表。查询条件聚合在 {@link ContestListQuery}：type/auth 过滤、startFrom/startTo 时间窗过滤、
     * window=recent 时按「距当前由近到远」排序（不改变可见性过滤）。
     */
    PageVo<ContestListVo> listContests(ContestListQuery query);
    PageVo<ContestListVo> listContests(ContestListQuery query, String participantUid);

    ContestDetailVo getContestDetail(Long contestId);

    ContestCheckVo checkContestExists(Long contestId);

    /**
     * Verify that a participant may access a problem in an active contest.
     *
     * @param contestId contest ID
     * @param problemId internal problem ID
     * @param uid participant UID
     */
    void checkProblemAccess(Long contestId, Long problemId, String uid);
}
