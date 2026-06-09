package com.hnieacm.judge.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 活跃题单展示对象
 */
@Data
public class ActiveTrainingVo {

    private Long id;

    private String title;

    private String author;

    private Integer status;

    private LocalDateTime gmtModified;
}
