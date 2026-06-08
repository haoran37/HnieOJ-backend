package com.hnieacm.auth.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/11
 * @Description: 用户信息实体
 */
@Data
@TableName("user_info")
public class UserInfo {

    @TableId
    private String uuid;
    
    private String uid;
    
    private String username;
    
    private String password;

    private Boolean passwordResetRequired;

    private String email;
    
    private String phone;
    
    private String avatar;
    
    private Long collegeId;
    
    private Long classId;

    private String grade;
    
    private String realname;

    private String qq;
    
    private Integer status;
    
    private String cfUsername;
    
    private String github;
    
    private String blog;
    
    private Boolean ipRestricted;
    
    /**
     * user_info.ip_whitelist 为 JSON Array，当前以 String 存储，业务层按需解析。
     */
    private String ipWhitelist;
    
    private LocalDateTime gmtCreate;
    
    private LocalDateTime gmtModified;
}
