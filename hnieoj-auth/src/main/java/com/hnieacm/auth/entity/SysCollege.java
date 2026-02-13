package com.hnieacm.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/12
 * @Description: 学院实体
 */
@Data
@TableName("sys_college")
public class SysCollege {

    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String name;
    
    private LocalDateTime gmtCreate;
    
    private LocalDateTime gmtModified;
}
