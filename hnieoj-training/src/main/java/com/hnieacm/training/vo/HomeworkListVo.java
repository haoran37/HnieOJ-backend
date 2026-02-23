package com.hnieacm.training.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 作业列表展示对象
 */
@Data
public class HomeworkListVo {

    private Long id;

    private String title;

    private String source;

    private String author;

    private Integer status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer problemCount;

    private Integer classCount;
}
