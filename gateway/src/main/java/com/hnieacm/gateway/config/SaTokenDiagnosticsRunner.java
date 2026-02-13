package com.hnieacm.gateway.config;

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
 * <p>
 * 用于快速定位“拿到 token 但网关仍未登录”的问题：
 * - token-name/token-prefix 是否与客户端一致
 * - SaTokenDao 是否为 Redis 实现（多服务共享登录态必须依赖共享存储）
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
