package com.hnieacm.user.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/14
 * @Description: 创建用户响应数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserVo {

    private String uid;

    /**
     * 当创建用户未显式传入密码时，后端会生成随机初始密码并返回给管理员
     * <p>
     * 说明：仅用于管理员分发/告知用户，前端可按需展示或忽略该字段。
     */
    private String initialPassword;
}

