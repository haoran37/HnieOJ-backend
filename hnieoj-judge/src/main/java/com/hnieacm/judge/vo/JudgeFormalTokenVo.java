package com.hnieacm.judge.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 正式判题节点长期 Token 轮换结果
 */
@Data
public class JudgeFormalTokenVo {

    private Long id;

    private Integer version;

    private String status;

    private String nacosDataId;

    private String nacosGroup;

    private LocalDateTime rotatedTime;
}
