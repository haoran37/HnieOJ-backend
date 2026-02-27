package com.hnieacm.contest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/27
 * @Description: 比赛报名实体（contest_register）
 */
@Data
@TableName("contest_register")
public class ContestRegister {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long cid;

    private String uid;

    private Integer status;

    private String type;

    private Long teamId;

    private LocalDateTime gmtCreate;
}
