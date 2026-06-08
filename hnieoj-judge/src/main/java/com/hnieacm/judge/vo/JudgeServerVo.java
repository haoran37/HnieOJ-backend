package com.hnieacm.judge.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/08
 * @Description: Legacy judge server view.
 */
@Data
public class JudgeServerVo {

    private Long id;

    private String name;

    private String ip;

    private Integer port;

    private Long taskNumber;

    private Integer maxTaskNumber;

    private Integer status;

    private LocalDateTime gmtCreate;

    private String nodeId;

    private String nodeType;

    private Boolean online;
}
