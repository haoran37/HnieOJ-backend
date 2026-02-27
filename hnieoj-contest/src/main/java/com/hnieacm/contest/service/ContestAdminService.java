package com.hnieacm.contest.service;

import com.hnieacm.common.dto.PageVo;
import com.hnieacm.contest.dto.AdminContestSaveRequest;
import com.hnieacm.contest.dto.AdminContestStatusRequest;
import com.hnieacm.contest.vo.AdminContestDetailVo;
import com.hnieacm.contest.vo.AdminContestListVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/27
 * @Description: 比赛管理服务
 */
public interface ContestAdminService {

    PageVo<AdminContestListVo> listContests(int page, int pageSize, String keyword);

    void createContest(AdminContestSaveRequest request);

    void updateContest(Long contestId, AdminContestSaveRequest request);

    void deleteContest(Long contestId);

    void changeContestStatus(AdminContestStatusRequest request);

    AdminContestDetailVo getContestDetail(Long contestId);
}
