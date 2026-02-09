package com.hnieacm.contest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/10
 * @Description: Contest Service 启动类
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ContestApplication {
    public static void main(String[] args) {
        SpringApplication.run(ContestApplication.class, args);
    }
}
