package com.hnieacm.judge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 判题节点 Bootstrap 一次性凭据（历史表名 judge_node_auth_code 保留）。
 *
 * <p>作为节点注册的 Bootstrap 事实记录，扩充 nodeType / policy / enrollment 绑定，
 * 避免与 judge_node_token 形成两套注册事实。只保存 bootstrap 的 SHA-256 摘要。</p>
 *
 * @author Codex
 */
@Data
@TableName("judge_node_auth_code")
public class JudgeNodeAuthCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Bootstrap 明文的 SHA-256 摘要。 */
    private String codeHash;

    private String nodeName;

    private String createdBy;

    private String remark;

    private Integer maxExchangeCount;

    private Integer usedCount;

    private String status;

    private LocalDateTime expireTime;

    private String nodeType;

    /** 策略 JSON：maxConcurrency / supportedJudgeModes / weight / authorizationUntil。 */
    private String policyJson;

    private LocalDateTime authorizationUntil;

    private String enrollmentId;

    private String publicKeyHash;

    private String nodeId;

    private LocalDateTime consumedTime;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
