package com.hnieacm.contest.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/27
 * @Description: 管理端比赛队伍列表展示对象
 */
@Data
public class AdminContestTeamListVo {

    private Long id;

    private Long cid;

    private String name;

    private String member1Uid;

    private String member1Name;

    private String member2Uid;

    private String member2Name;

    private String member3Uid;

    private String member3Name;

    private LocalDateTime createTime;
}
