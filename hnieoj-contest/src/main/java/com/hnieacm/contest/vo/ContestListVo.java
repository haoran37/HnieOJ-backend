package com.hnieacm.contest.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 竞赛列表展示对象
 */
@Data
public class ContestListVo {

    private Long id;

    private String title;

    private String type;

    private String auth;

    private String source;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String status;

    private Integer problemCount;

    private List<String> customTags;
}
