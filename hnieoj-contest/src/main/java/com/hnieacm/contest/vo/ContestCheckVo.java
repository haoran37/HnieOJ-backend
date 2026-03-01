package com.hnieacm.contest.vo;

import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/03/01
 * @Description: 比赛有效性检查展示对象
 */
@Data
public class ContestCheckVo {

    private Boolean valid;

    private Long contestId;

    private String title;
}
