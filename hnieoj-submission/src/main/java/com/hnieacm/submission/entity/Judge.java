package com.hnieacm.submission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 数据库实体类，对应表：judge
 */
@Data
@TableName("judge")
public class Judge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String submitId;

    @TableField("problem_id")
    private Long problemId;

    @TableField("problem_code")
    private String problemCode;

    private String uid;

    private String username;

    private String language;

    private String code;

    private Integer status;

    private String errorMessage;

    private Integer time;

    private Integer memory;

    private Integer score;

    private Long cid;

    private Integer totalCase;

    private Integer judgedCase;

    private Integer currentCase;

    private Long cpid;

    private Long tid;

    private Long hid;

    private String judger;

    private String ip;

    private Boolean isManual;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
