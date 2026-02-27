package com.hnieacm.contest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/27
 * @Description: 比赛队伍实体（contest_team）
 */
@Data
@TableName("contest_team")
public class ContestTeam {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long cid;

    private String name;

    private String captainUid;

    private LocalDateTime gmtCreate;
}
