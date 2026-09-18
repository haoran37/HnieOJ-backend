package com.hnieacm.judge.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 判题节点安全配置
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "hnieoj.judge.security")
public class JudgeSecurityProperties {
    //TODO
    private String jwtSecret = "replace_me_judge_jwt_secret";

    private long tempTokenTtlSeconds = 7200;

    /**
     * 正式节点短期 Token 的续期时长，单位秒。
     */
    private long formalTokenTtlSeconds = 2592000;

    private long authCodeTtlSeconds = 1800;

    private int authCodeMaxExchange = 1;

    private long nodeActiveTimeoutSeconds = 90;

    /**
     * 临时节点首次接入时由服务端固定的核准并发上限，节点心跳不能提高。
     */
    private int tempNodeDefaultMaxConcurrency = 1;

    /**
     * 临时节点首次接入时由服务端固定的允许判题模式集合。
     */
    private List<String> tempNodeAllowedModes = List.of("default");
}
