package com.hnieacm.achievement.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 用户信息（仅用于校验用户是否存在）
 */
@Data
@TableName("user_info")
public class UserInfo {

    @TableId
    private String uuid;

    private String uid;
}

