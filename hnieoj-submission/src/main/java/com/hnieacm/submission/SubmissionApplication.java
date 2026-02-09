package com.hnieacm.submission;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/10
 * @Description: Submission Service 启动类
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class SubmissionApplication {
    public static void main(String[] args) {
        SpringApplication.run(SubmissionApplication.class, args);
    }
}
