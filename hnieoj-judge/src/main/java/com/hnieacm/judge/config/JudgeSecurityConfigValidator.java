package com.hnieacm.judge.config;

import cn.hutool.core.util.StrUtil;
import com.hnieacm.judge.properties.JudgeSecurityProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/08
 * @Description: Judge security configuration startup validator
 */
@Component
@RequiredArgsConstructor
public class JudgeSecurityConfigValidator {

    private static final String DEFAULT_SECRET_MARK = "replace_me";
    private static final int MIN_JWT_SECRET_LENGTH = 32;

    private final JudgeSecurityProperties judgeSecurityProperties;

    @PostConstruct
    public void validate() {
        String jwtSecret = judgeSecurityProperties.getJwtSecret();
        if (StrUtil.isBlank(jwtSecret) || jwtSecret.contains(DEFAULT_SECRET_MARK)) {
            throw new IllegalStateException("HNIEOJ_JUDGE_JWT_SECRET must be configured by environment variable");
        }
        if (jwtSecret.length() < MIN_JWT_SECRET_LENGTH) {
            throw new IllegalStateException("HNIEOJ_JUDGE_JWT_SECRET length must be at least 32 characters");
        }
    }
}
