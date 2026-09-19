package com.hnieacm.judge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/03/01
 * @Description: 远程评测账号实体
 */
@Data
@TableName("remote_judge_account")
public class RemoteJudgeAccount {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String oj;

    private String username;

    private String password;

    private Integer status;

    private Integer maxConcurrency;

    private LocalDateTime gmtCreate;
}
