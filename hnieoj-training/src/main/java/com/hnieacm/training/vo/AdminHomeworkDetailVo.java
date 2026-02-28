package com.hnieacm.training.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/28
 * @Description: 管理端作业详情展示对象
 */
@Data
public class AdminHomeworkDetailVo {

    private Long id;

    private String title;

    private String description;

    private String source;

    private String author;

    private Boolean status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private List<Long> timeRange;

    private List<Long> classIds;

    private List<HomeworkProblemVo> problems;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
