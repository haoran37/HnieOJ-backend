package com.hnieacm.announcement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.mybatis.spring.annotation.MapperScan;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/10
 * @Description: 公告新闻服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.hnieacm")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.hnieacm.announcement.feign")
@MapperScan("com.hnieacm.announcement.mapper")
public class AnnouncementApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnnouncementApplication.class, args);
    }
}
