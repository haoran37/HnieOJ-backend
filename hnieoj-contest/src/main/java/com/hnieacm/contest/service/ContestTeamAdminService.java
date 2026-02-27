package com.hnieacm.contest.service;

import com.hnieacm.common.dto.PageVo;
import com.hnieacm.contest.dto.AdminContestTeamSaveRequest;
import com.hnieacm.contest.dto.BatchDeleteContestTeamRequest;
import com.hnieacm.contest.vo.AdminContestTeamListVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/27
 * @Description: 比赛队伍管理服务
 */
public interface ContestTeamAdminService {

    PageVo<AdminContestTeamListVo> listTeams(Long contestId, int page, int pageSize);

    void createTeam(AdminContestTeamSaveRequest request);

    void updateTeam(Long teamId, AdminContestTeamSaveRequest request);

    void deleteTeam(Long teamId);

    void batchDeleteTeam(BatchDeleteContestTeamRequest request);
}
