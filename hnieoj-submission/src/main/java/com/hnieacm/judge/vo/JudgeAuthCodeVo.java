package com.hnieacm.judge.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 临时判题节点授权码响应
 */
@Data
public class JudgeAuthCodeVo {

    private Long id;

    private String authCode;

    private String nodeName;

    private Integer maxExchangeCount;

    private LocalDateTime expireTime;
}
