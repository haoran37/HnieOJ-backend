package com.hnieacm.user.dto;

import lombok.Data;
import lombok.ToString;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 用户自助修改密码请求。toString 排除密码，避免日志/异常信息回显明文。
 */
@Data
@ToString(exclude = {"oldPassword", "newPassword"})
public class UpdatePasswordRequest {

    private String oldPassword;

    private String newPassword;
}
