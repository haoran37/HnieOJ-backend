package com.hnieacm.contest.service;

import com.hnieacm.common.dto.PageVo;
import com.hnieacm.contest.vo.ContestDetailVo;
import com.hnieacm.contest.vo.ContestListVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 比赛查询服务
 */
public interface ContestQueryService {

    PageVo<ContestListVo> listContests(int page, int pageSize, String type, String auth);

    ContestDetailVo getContestDetail(Long contestId);
}
