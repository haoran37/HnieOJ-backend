package com.hnieacm.judge.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 正式判题节点长期 Token 密文配置
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "hnieoj.judge.formal-token")
public class JudgeFormalTokenProperties {

    /**
     * 使用公钥加密后的长期 Token，Base64 编码，可选 {rsa} 前缀。
     */
    private String encryptedToken;

    /**
     * PKCS#8 PEM 私钥内容。生产环境建议通过环境变量注入。
     */
    private String privateKey;

    /**
     * PKCS#8 PEM 私钥文件路径。优先级低于 privateKey。
     */
    private String privateKeyPath;

    private String cipherAlgorithm = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

}
