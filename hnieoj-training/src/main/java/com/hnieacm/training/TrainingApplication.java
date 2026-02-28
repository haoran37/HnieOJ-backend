package com.hnieacm.training;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 题单与作业服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.hnieacm")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.hnieacm.training.feign")
@MapperScan("com.hnieacm.training.mapper")
public class TrainingApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrainingApplication.class, args);
    }
}
