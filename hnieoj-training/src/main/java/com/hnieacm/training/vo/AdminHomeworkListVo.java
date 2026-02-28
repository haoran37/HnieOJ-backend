package com.hnieacm.training.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/28
 * @Description: 管理端作业列表展示对象
 */
@Data
public class AdminHomeworkListVo {

    private Long id;

    private String title;

    private String source;

    private String author;

    private Boolean status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer problemCount;

    private Integer classCount;

    private LocalDateTime gmtCreate;
}
