package com.hnieacm.user.vo;

import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/27
 * @Description: 用户存在性检查展示对象
 */
@Data
public class UserCheckVo {

    private Boolean exists;

    private String uid;

    private String username;
}
