package com.hnieacm.submission.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/11
 * @Description: 提交模块 WebSocket 配置
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "hnieoj.submission.websocket")
public class SubmissionWebSocketProperties {

    private List<String> allowedOrigins = new ArrayList<>(List.of("*"));
}
