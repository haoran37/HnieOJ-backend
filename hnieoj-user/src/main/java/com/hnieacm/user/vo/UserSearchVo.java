package com.hnieacm.user.vo;

import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/14
 * @Description: 用户搜索展示对象
 */
@Data
public class UserSearchVo {

    private String uid;

    private String username;

    private String majorClass;

    private String college;

    private List<String> roles;
}
