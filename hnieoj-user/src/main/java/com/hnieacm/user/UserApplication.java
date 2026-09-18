package com.hnieacm.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.mybatis.spring.annotation.MapperScan;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/10
 * @Description: User Service 启动类（合并认证与成就域）
 */
@SpringBootApplication(scanBasePackages = "com.hnieacm")
@EnableDiscoveryClient
@MapperScan({"com.hnieacm.user.mapper", "com.hnieacm.auth.mapper", "com.hnieacm.achievement.mapper"})
public class UserApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
