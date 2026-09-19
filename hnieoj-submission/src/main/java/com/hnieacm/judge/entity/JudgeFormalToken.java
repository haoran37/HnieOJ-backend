package com.hnieacm.judge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 正式判题节点长期 Token 哈希记录
 */
@Data
@TableName("judge_formal_token")
public class JudgeFormalToken {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer version;

    private String tokenHash;

    private String hashAlgorithm;

    private String encryptedToken;

    private String status;

    private String rotatedBy;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
