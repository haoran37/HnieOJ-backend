package com.hnieacm.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

/**
 * @Author: HnieOJ contributors
 * @Description: One-time registration invitation; only the code hash is stored.
 */
@Data
@TableName("invite_code")
public class InviteCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    @JsonIgnore
    private String codeHash;

    private Integer status;

    private String createdBy;

    private String usedUid;

    private LocalDateTime usedAt;

    private LocalDateTime expiresAt;

    private LocalDateTime gmtCreate;
}
