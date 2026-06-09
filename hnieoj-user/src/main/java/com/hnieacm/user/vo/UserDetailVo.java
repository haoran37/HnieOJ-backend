package com.hnieacm.user.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 用户详情（公开字段，用于展示）
 */
@Data
public class UserDetailVo {

    private String uid;

    private String username;

    private String realname;

    private String avatar;

    private Long collegeId;

    private String college;

    private String grade;

    private Long classId;

    private String majorClass;

    @JsonProperty("cf_username")
    private String cfUsername;

    private String github;

    private String blog;

    private List<String> roles;
}
