package com.hnieacm.submission.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 提交模块配置
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "hnieoj.submission")
public class SubmissionProperties {

    /**
     * 单次提交代码最大字节数，按 UTF-8 编码计算。
     */
    private Integer maxCodeBytes = 65536;
}
