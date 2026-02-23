package com.hnieacm.contest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 竞赛实体（contest）
 */
@Data
@TableName("contest")
public class Contest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String uid;

    private String author;

    private String title;

    private String description;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer type;

    private Integer auth;

    private String pwd;

    private String source;

    private Integer isVisible;

    private Integer status;

    private String rankShowName;

    private Integer openRank;

    private Integer sealRank;

    private LocalDateTime sealRankTime;

    /**
     * 自定义标签 JSON 字符串
     */
    private String customTags;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
