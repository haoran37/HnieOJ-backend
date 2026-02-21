package com.hnieacm.problem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 数据库实体类，对应表：language
 */
@Data
@TableName("language")
public class Language {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String contentType;

    private String compileCommand;

    private Boolean isSpj;

    private LocalDateTime gmtCreate;
}

