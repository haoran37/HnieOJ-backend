package com.hnieacm.judge.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 正式判题节点长期 Token 配置
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "hnieoj.judge.formal-token")
public class JudgeFormalTokenProperties {

    /**
     * PKCS#8 PEM 公钥内容。生产环境建议使用 publicKeyPath。
     */
    private String publicKey;

    /**
     * PKCS#8 PEM 公钥文件路径。后端只需要公钥，不持有正式节点私钥。
     */
    private String publicKeyPath;

    private String cipherAlgorithm = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    private String nacosDataId = "hnieoj-judge-formal-token.yaml";

    private String nacosGroup = "HNIEOJ_SECRET_GROUP";
}
