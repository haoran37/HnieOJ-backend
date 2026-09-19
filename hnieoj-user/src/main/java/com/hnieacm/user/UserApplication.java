package com.hnieacm.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.mybatis.spring.annotation.MapperScan;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/10
 * @Description: User Service 启动类（合并认证与成就域）
 * <p>
 * 合并后仍保留 EnableFeignClients 以支持跨服务的 SubmissionInternalFeignClient；
 * 原本地 auth 自调用已改为同进程 Service 直调。
 */
@SpringBootApplication(scanBasePackages = "com.hnieacm")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.hnieacm.user.feign")
@MapperScan({"com.hnieacm.user.mapper", "com.hnieacm.auth.mapper", "com.hnieacm.achievement.mapper"})
public class UserApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
