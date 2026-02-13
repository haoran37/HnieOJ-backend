package com.hnieacm.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/13
 * @Description: 用户-角色关联表实体
 */
@Data
@TableName("user_role")
public class UserRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userUid;

    private Long roleId;
}

