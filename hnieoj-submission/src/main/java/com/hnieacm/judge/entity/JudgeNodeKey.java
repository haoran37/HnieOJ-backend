package com.hnieacm.judge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 判题节点公钥历史与轮换状态。
 *
 * <p>只保存公钥与状态机元数据，绝不保存私钥或 bootstrap 明文。初始注册密钥
 * rotationId 为空；轮换密钥先 PENDING，确认后 ACTIVE，旧密钥进入 GRACE。</p>
 *
 * @author Codex
 */
@Data
@TableName("judge_node_key")
public class JudgeNodeKey {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String keyId;

    private String nodeId;

    private String publicKey;

    private String publicKeyHash;

    /** PENDING / ACTIVE / GRACE / REVOKED / EXPIRED */
    private String status;

    private String rotationId;

    private String confirmNonce;

    private LocalDateTime expiresAt;

    private String previousKeyId;

    private LocalDateTime activatedAt;

    private LocalDateTime revokedTime;

    private String revokedBy;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
