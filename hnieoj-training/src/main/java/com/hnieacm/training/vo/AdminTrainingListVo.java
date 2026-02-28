package com.hnieacm.training.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/28
 * @Description: 管理端题单列表展示对象
 */
@Data
public class AdminTrainingListVo {

    private Long id;

    private String title;

    private String type;

    private String auth;

    private String author;

    private Boolean status;

    private Integer rank;

    private Integer problemCount;

    private LocalDateTime gmtCreate;
}
