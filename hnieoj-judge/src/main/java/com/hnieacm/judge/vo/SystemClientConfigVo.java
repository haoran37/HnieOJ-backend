package com.hnieacm.judge.vo;

import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 客户端系统配置展示对象
 */
@Data
public class SystemClientConfigVo {

    private String websiteName;

    private String logoUrl;

    private String icpCode;

    private Integer submissionInterval;

    private String registerMode;

    private List<String> allowedEmailSuffixes;
}
