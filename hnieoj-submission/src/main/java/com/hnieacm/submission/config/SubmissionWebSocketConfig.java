package com.hnieacm.submission.config;

import com.hnieacm.submission.properties.SubmissionWebSocketProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/11
 * @Description: 提交判题进度 STOMP WebSocket 配置
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class SubmissionWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final SubmissionWebSocketProperties webSocketProperties;
    private final SubmissionWebSocketAuthChannelInterceptor authChannelInterceptor;

    /**
     * @MethodName registerStompEndpoints
     * @Param registry
     * @Description 注册 Stomp 端点
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/05/11
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/submissions")
                .setAllowedOriginPatterns(webSocketProperties.getAllowedOrigins().toArray(String[]::new));
    }

    /**
     * @MethodName configureMessageBroker
     * @Param registry
     * @Description 配置消息代理
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/05/11
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    /**
     * @MethodName configureClientInboundChannel
     * @Param registration
     * @Description 配置客户端入站通道
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/05/11
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }
}
