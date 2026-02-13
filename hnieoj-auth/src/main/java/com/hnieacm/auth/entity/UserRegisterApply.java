package com.hnieacm.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/12
 * @Description: 用户注册申请实体
 */
@Data
@TableName("user_register_apply")
public class UserRegisterApply {

    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String uid;
    
    private String username;
    
    private String password;
    
    private String email;
    
    private Long collegeId;
    
    private Long classId;
    
    private String grade;
    
    private String qq;
    
    private Integer status;
    
    private String replyInfo;
    
    private LocalDateTime gmtCreate;
    
    private LocalDateTime gmtModified;
}
