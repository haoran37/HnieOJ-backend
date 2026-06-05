package com.hnieacm.judge.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/03/01
 * @Description: 系统配置展示对象
 */
@Data
public class SystemConfigVo {

    private String websiteName;

    private String logoUrl;

    private String icpCode;

    private Boolean allowRegister;

    private String registerMode;

    private List<String> allowedEmailSuffixes;

    private String smtpHost;

    private Integer smtpPort;

    private String smtpEmail;

    private String smtpNickname;

    private Integer submissionInterval;

    private LocalDateTime gmtModified;
}
