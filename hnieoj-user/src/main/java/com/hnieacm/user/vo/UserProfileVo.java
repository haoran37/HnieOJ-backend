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

    /**
     * 学院 ID，供前端自助资料表单级联选择；原名称字段保留。
     */
    private Long collegeId;

    @JsonProperty("class")
    private String className;

    /**
     * 班级 ID，供前端自助资料表单级联选择；原名称字段保留。
     */
    private Long classId;
}
