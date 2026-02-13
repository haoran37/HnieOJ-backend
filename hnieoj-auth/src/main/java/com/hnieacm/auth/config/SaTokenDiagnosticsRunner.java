package com.hnieacm.auth.config;

import cn.dev33.satoken.SaManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/13
 * @Description: Sa-Token 关键配置诊断（仅 dev）。
 */
@Slf4j
@Profile("dev")
@Component
@RefreshScope
public class SaTokenDiagnosticsRunner implements CommandLineRunner {

    @Value("${hnieoj.internal.token:}")
    private String internalToken;

    @Override
    public void run(String... args) {
        try {
            log.info("Sa-Token config - tokenName: {}, tokenPrefix: {}",
                    SaManager.getConfig().getTokenName(),
                    SaManager.getConfig().getTokenPrefix());
            log.info("Sa-Token dao: {}", SaManager.getSaTokenDao().getClass().getName());
            log.info("Internal token configured: {}", internalToken != null && !internalToken.isBlank());
        } catch (Exception e) {
            log.warn("Sa-Token diagnostics failed", e);
        }
    }
}
