package com.hnieacm.judge.config;

import com.hnieacm.common.judge.NodeProtocolConstants;
import com.hnieacm.judge.properties.NodeSecurityProperties;
import com.hnieacm.judge.ws.JudgeNodeWebSocketHandler;
import jakarta.servlet.ServletContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * 节点协议 WebSocket 端点注册。
 *
 * <p>独立于现有用户 STOMP/ws/submissions 通道；节点侧在应用层完成 Ed25519 认证，
 * 因此这里不启用用户拦截器，改为由 {@link JudgeNodeWebSocketHandler} 逐帧鉴权。
 * 容器级帧缓冲必须与协议声明的 task/control 上限一致，否则容器会在 handler 之前
 * 直接关闭超限连接，导致“过大控制帧应被 ERROR 拒绝”的行为退化为断连。</p>
 *
 * @author Codex
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class JudgeNodeWebSocketConfig implements WebSocketConfigurer {

    private static final String SERVER_CONTAINER_ATTRIBUTE = "jakarta.websocket.server.ServerContainer";

    private final JudgeNodeWebSocketHandler judgeNodeWebSocketHandler;
    private final NodeSecurityProperties nodeSecurityProperties;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(judgeNodeWebSocketHandler, NodeProtocolConstants.WS_PATH_JUDGE_NODE)
                .setAllowedOriginPatterns("*");
    }

    /**
     * 将嵌入式容器的文本/二进制帧缓冲对齐到协议 task 上限，使超大帧进入应用层判定而非被容器断连。
     *
     * <p>仅在真实 servlet 容器（ServletContext 已挂载 {@code ServerContainer}）时创建；
     * MOCK 测试上下文没有该属性，返回 null 以避免启动失败。</p>
     *
     * @param servletContext 当前 servlet 上下文
     * @return 容器工厂；无真实 WebSocket 容器时为 null
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer(ServletContext servletContext) {
        if (servletContext == null || servletContext.getAttribute(SERVER_CONTAINER_ATTRIBUTE) == null) {
            return null;
        }
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(nodeSecurityProperties.getMaxTaskFrameBytes());
        container.setMaxBinaryMessageBufferSize(nodeSecurityProperties.getMaxTaskFrameBytes());
        return container;
    }
}
