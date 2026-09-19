package com.hnieacm.judge.config;

import com.hnieacm.judge.properties.NodeSecurityProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 节点安全配置 fail-fast 校验测试。
 *
 * @author Codex
 */
class NodeSecurityConfigValidatorTest {

    private NodeSecurityProperties validProperties() {
        NodeSecurityProperties properties = new NodeSecurityProperties();
        properties.setAccessTokenSecret("0123456789abcdef0123456789abcdef");
        properties.setAudience("hnieoj-judge-node");
        return properties;
    }

    @Test
    void acceptsValidDefaults() {
        assertDoesNotThrow(() -> new NodeSecurityConfigValidator(validProperties()).validate());
    }

    @Test
    void rejectsPlaceholderSecret() {
        NodeSecurityProperties properties = validProperties();
        properties.setAccessTokenSecret("replace_me");
        assertThrows(IllegalStateException.class,
                () -> new NodeSecurityConfigValidator(properties).validate());
    }

    @Test
    void rejectsTooSmallFrameBound() {
        NodeSecurityProperties properties = validProperties();
        properties.setMaxControlFrameBytes(100);
        assertThrows(IllegalStateException.class,
                () -> new NodeSecurityConfigValidator(properties).validate());
    }

    @Test
    void rejectsTaskFrameSmallerThanControlFrame() {
        NodeSecurityProperties properties = validProperties();
        properties.setMaxControlFrameBytes(65536);
        properties.setMaxTaskFrameBytes(1024);
        assertThrows(IllegalStateException.class,
                () -> new NodeSecurityConfigValidator(properties).validate());
    }

    @Test
    void rejectsNonPositiveTtl() {
        NodeSecurityProperties properties = validProperties();
        properties.setChallengeTtlSeconds(0);
        assertThrows(IllegalStateException.class,
                () -> new NodeSecurityConfigValidator(properties).validate());
    }
}
