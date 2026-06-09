package com.hnieacm.judge.vo;

import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 判题状态计数展示对象
 */
@Data
public class DashboardStatusCountVo {

    private Integer status;

    private String statusText;

    private Long count;
}
