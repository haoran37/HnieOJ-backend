package com.hnieacm.user.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 用户管理相关配置
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "hnieoj.user.manage")
public class UserManageProperties {

    private int usernameMinLength = 2;

    private int usernameMaxLength = 20;

    private int passwordMinLength = 6;

    private int passwordMaxLength = 32;

    /**
     * 创建用户未传入密码时生成的随机密码长度
     */
    private int generatedPasswordLength = 12;

    /**
     * 可在权限管理中授予/更新/撤销的角色ID列表
     */
    private List<Long> manageableRoleIds = new ArrayList<>(List.of(1001L, 1002L, 1003L));

    /**
     * 随机密码字符集
     */
    private String passwordChars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*";
}

