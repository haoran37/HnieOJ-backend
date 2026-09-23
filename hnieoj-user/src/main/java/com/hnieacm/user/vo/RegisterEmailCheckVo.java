package com.hnieacm.user.vo;

import lombok.Data;

/**
 * @Author: HnieOJ contributors
 * @Description: Public registration policy check returned by the system service.
 */
@Data
public class RegisterEmailCheckVo {

    private Boolean allowRegister;

    private Boolean matched;

    private String registerMode;

    private String reason;
}
