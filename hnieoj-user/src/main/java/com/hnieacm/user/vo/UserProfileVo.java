package com.hnieacm.user.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/14
 * @Description: 用户信息展示对象
 */
@Data
public class UserProfileVo {

    private String uid;

    private String username;

    private String email;

    private String phone;

    private String avatar;

    private String qq;

    private String grade;

    private String realname;

    @JsonProperty("cf_username")
    private String cfUsername;

    private String github;

    private String blog;

    private List<String> roles;

    private String college;

    @JsonProperty("class")
    private String className;
}
