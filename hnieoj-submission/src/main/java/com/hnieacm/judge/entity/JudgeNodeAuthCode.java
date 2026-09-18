package com.hnieacm.judge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 临时判题节点授权码
 */
@Data
@TableName("judge_node_auth_code")
public class JudgeNodeAuthCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String codeHash;

    private String nodeName;

    private String createdBy;

    private String remark;

    private Integer maxExchangeCount;

    private Integer usedCount;

    private String status;

    private LocalDateTime expireTime;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
