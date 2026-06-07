package com.hnieacm.submission;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/10
 * @Description: Submission Service 启动类
 */
@SpringBootApplication(scanBasePackages = "com.hnieacm")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.hnieacm.submission.feign")
@MapperScan("com.hnieacm.submission.mapper")
@EnableScheduling
public class SubmissionApplication {
    public static void main(String[] args) {
        SpringApplication.run(SubmissionApplication.class, args);
    }
}
