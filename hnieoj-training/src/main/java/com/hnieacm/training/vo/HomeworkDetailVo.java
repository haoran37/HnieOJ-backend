package com.hnieacm.training.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 作业详情展示对象
 */
@Data
public class HomeworkDetailVo {

    private Long id;

    private String title;

    private String description;

    private String source;

    private String author;

    private Integer status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private List<Long> classIds;

    private List<HomeworkProblemVo> problems;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
