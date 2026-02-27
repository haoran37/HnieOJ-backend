package com.hnieacm.contest.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/27
 * @Description: 管理端比赛列表展示对象
 */
@Data
public class AdminContestListVo {

    private Long id;

    private String title;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String type;

    private String auth;

    private String permission;

    private String source;

    private String author;

    private Boolean status;

    private String runtimeStatus;

    private Integer problemCount;

    private List<String> customTags;
}
