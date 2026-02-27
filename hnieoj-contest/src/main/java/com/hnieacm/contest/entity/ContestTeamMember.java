package com.hnieacm.contest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/27
 * @Description: 比赛队伍成员实体（contest_team_member）
 */
@Data
@TableName("contest_team_member")
public class ContestTeamMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teamId;

    private String uid;

    private Integer isCaptain;
}
