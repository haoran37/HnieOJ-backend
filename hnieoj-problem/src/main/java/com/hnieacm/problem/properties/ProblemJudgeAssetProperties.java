package com.hnieacm.problem.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/08
 * @Description: 题目判题资产配置
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "hnieoj.problem.judge-asset")
public class ProblemJudgeAssetProperties {

    private Integer maxCheckerBytes = 262144;

    private Integer maxInteractorBytes = 262144;
}
