package com.hnieacm.user.dto;

import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 用户自助修改资料请求。
 * <p>
 * 字段白名单：仅 username/avatar/qq/github/blog 可修改。字段为 null 表示“未传”，
 * 保留原值；空字符串表示清除该可选项。uid/email/role/password/college 等不在本 DTO 中，
 * 客户端即使提交也不会被采用。
 */
@Data
public class UpdateUserProfileRequest {

    private String username;

    private String avatar;

    private String qq;

    private String github;

    private String blog;
}
