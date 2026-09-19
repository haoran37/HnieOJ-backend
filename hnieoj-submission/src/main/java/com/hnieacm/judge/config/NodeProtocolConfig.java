package com.hnieacm.judge.config;

import com.hnieacm.common.judge.NodeAccessTokenService;
import com.hnieacm.judge.properties.NodeSecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 节点协议共享组件装配。
 *
 * @author Codex
 */
@Configuration
@RequiredArgsConstructor
public class NodeProtocolConfig {

    private final NodeSecurityProperties nodeSecurityProperties;

    @Bean
    public NodeAccessTokenService nodeAccessTokenService() {
        return new NodeAccessTokenService(
                nodeSecurityProperties.getAccessTokenSecret(),
                nodeSecurityProperties.getAudience(),
                nodeSecurityProperties.getAccessTokenTtlSeconds());
    }
}
