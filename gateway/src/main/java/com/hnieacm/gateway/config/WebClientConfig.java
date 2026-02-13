package com.hnieacm.gateway.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
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
public class WebClientConfig {
    /**
     * 超时配置
     * TODO: 将参数移入 nacos 中
     */
    // 连接超时：3秒
    private static final int CONNECT_TIMEOUT_MS = 3000;
    // 读取超时：10秒
    private static final int READ_TIMEOUT_SECONDS = 10;
    // 写入超时：10秒
    private static final int WRITE_TIMEOUT_SECONDS = 10;
    // 响应超时：10秒
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(10);

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
        HttpClient httpClient = HttpClient.create()
                // 连接超时配置
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
                // 响应超时配置
                .responseTimeout(RESPONSE_TIMEOUT)
                // 读写超时配置
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                );

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
