package com.hnieacm.judge.vo;

import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/03/01
 * @Description: 系统时间展示对象
 */
@Data
public class SystemTimeVo {

    private String serverTime;

    private String timezone;

    private Long unixTimestamp;
}
