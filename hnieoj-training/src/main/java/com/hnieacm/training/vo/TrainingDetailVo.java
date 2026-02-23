package com.hnieacm.training.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 题单详情展示对象
 */
@Data
public class TrainingDetailVo {

    private Long id;

    private String title;

    private String description;

    private String author;

    private String type;

    private String auth;

    private Integer status;

    private Integer rank;

    private Integer problemCount;

    private List<String> categories;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
