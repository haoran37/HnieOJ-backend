package com.hnieacm.training.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 题单列表展示对象
 */
@Data
public class TrainingListVo {

    private Long id;

    private String title;

    private String type;

    private String auth;

    private String author;

    private Integer status;

    private Integer rank;

    private Integer problemCount;

    private List<String> categories;

    private LocalDateTime gmtCreate;
}
