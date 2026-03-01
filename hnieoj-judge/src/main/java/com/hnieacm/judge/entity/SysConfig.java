package com.hnieacm.judge.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hnieacm.judge.constant.SystemConfigConstant;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/03/01
 * @Description: 系统配置实体
 */
@Data
@TableName(SystemConfigConstant.SYS_CONFIG_TABLE_NAME)
public class SysConfig {

    @TableId
    private Integer id;

    private String websiteName;

    private String logoUrl;

    private String icpCode;

    private Boolean allowRegister;

    private String registerMode;

    /**
     * allowed_email_suffixes 为 JSON 字符串。
     */
    private String allowedEmailSuffixes;

    private String smtpHost;

    private Integer smtpPort;

    private String smtpEmail;

    private String smtpPassword;

    private String smtpNickname;

    private String judgeToken;

    private Integer submissionInterval;

    private LocalDateTime gmtModified;
}
