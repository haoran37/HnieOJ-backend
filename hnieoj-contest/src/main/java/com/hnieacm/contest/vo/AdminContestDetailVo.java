package com.hnieacm.contest.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/27
 * @Description: 管理端比赛详情展示对象
 */
@Data
public class AdminContestDetailVo {

    private Long id;

    private String uid;

    private String author;

    private String title;

    private String description;

    private String type;

    private String auth;

    private String permission;

    private String source;

    private Boolean status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private List<Long> timeRange;

    private String runtimeStatus;

    private String rankShowName;

    private Boolean openRank;

    private Boolean sealRank;

    private LocalDateTime sealRankTime;

    private List<String> customTags;

    private List<ContestProblemVo> problems;

    private List<AdminContestAccountVo> accountList;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
