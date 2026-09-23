package com.hnieacm.gateway.config;

import com.hnieacm.gateway.properties.WebClientTimeoutProperties;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/13
 * @Description: WebClient 配置
 */
@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final WebClientTimeoutProperties webClientTimeoutProperties;

    /**
     * @MethodName webClientBuilder
     * <p>
     * @Description 创建支持负载均衡的 WebClient 构建器
     * @Return @return {@link WebClient.Builder }
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    @Bean
    @LoadBalanced
    public WebClient.Builder webClientBuilder() {
        Duration responseTimeout = Duration.ofSeconds(webClientTimeoutProperties.getResponseTimeoutSeconds());
        HttpClient httpClient = HttpClient.create()
                // 连接超时配置
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, webClientTimeoutProperties.getConnectTimeoutMs())
                // 响应超时配置
                .responseTimeout(responseTimeout)
                // 读写超时配置
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(webClientTimeoutProperties.getReadTimeoutSeconds(), TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(webClientTimeoutProperties.getWriteTimeoutSeconds(), TimeUnit.SECONDS))
                );

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
