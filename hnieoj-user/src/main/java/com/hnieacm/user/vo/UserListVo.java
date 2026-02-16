package com.hnieacm.user.vo;

import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 用户列表展示对象
 */
@Data
public class UserListVo {

    private String uid;

    private String username;

    private String realname;

    private String avatar;

    private Long collegeId;

    private String college;

    private String grade;

    private Long classId;

    private String majorClass;

    private Integer status;

    private List<String> roles;
}
