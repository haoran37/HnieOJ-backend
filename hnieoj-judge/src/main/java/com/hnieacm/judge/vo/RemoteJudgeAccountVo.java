package com.hnieacm.judge.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/03/01
 * @Description: 远程评测账号展示对象
 */
@Data
public class RemoteJudgeAccountVo {

    private Integer id;

    private String oj;

    private String username;

    private Integer status;

    private Integer maxConcurrency;

    private LocalDateTime gmtCreate;
}
