package com.hnieacm.judge.vo;

import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 邮箱注册策略校验结果
 */
@Data
public class EmailCheckVo {

    private Boolean allowRegister;

    private Boolean matched;

    private String reason;
}
