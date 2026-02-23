package com.hnieacm.contest.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 竞赛详情展示对象
 */
@Data
public class ContestDetailVo {

    private Long id;

    private String uid;

    private String author;

    private String title;

    private String description;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String type;

    private String auth;

    private String source;

    private String status;

    private String rankShowName;

    private Boolean openRank;

    private Boolean sealRank;

    private LocalDateTime sealRankTime;

    private Integer problemCount;

    private List<String> customTags;

    private List<ContestProblemVo> problems;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
