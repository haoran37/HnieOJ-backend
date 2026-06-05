package com.hnieacm.judge.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.judge.properties.JudgeFormalTokenProperties;
import com.hnieacm.judge.service.FormalJudgeTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 正式判题节点长期 Token 校验服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormalJudgeTokenServiceImpl implements FormalJudgeTokenService {

    private static final String RSA_PREFIX = "{rsa}";
    private static final String RSA_COLON_PREFIX = "rsa:";
    private static final String PRIVATE_KEY_BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String PRIVATE_KEY_END = "-----END PRIVATE KEY-----";
    private static final String RSA_PRIVATE_KEY_BEGIN = "-----BEGIN RSA PRIVATE KEY-----";
    private static final String RSA_PRIVATE_KEY_END = "-----END RSA PRIVATE KEY-----";
    private static final String RSA_KEY_ALGORITHM = "RSA";
    private static final String OAEP_SHA256_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    private final JudgeFormalTokenProperties formalTokenProperties;

    /**
     * @MethodName matches
     * @Param rawToken
     * @Description 验证判题Token
     * @Return @return boolean
     * @Author HaoRan_Lyu
     * @Date 2026/06/05
     */
    @Override
    public boolean matches(String rawToken) {
        String normalizedToken = StrUtil.trimToNull(rawToken);
        if (normalizedToken == null) {
            return false;
        }

        String encryptedToken = StrUtil.trimToNull(formalTokenProperties.getEncryptedToken());
        if (encryptedToken == null) {
            log.warn("Formal judge encrypted token is not configured");
            return false;
        }

        String expectedToken = decryptToken(encryptedToken);
        return constantTimeEquals(normalizedToken, expectedToken);
    }

    /**
     * @MethodName decryptToken
     * @Param encryptedToken
     * @Description 解密令牌
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/06/05
     */
    private String decryptToken(String encryptedToken) {
        try {
            String cipherAlgorithm = StrUtil.blankToDefault(
                    formalTokenProperties.getCipherAlgorithm(),
                    OAEP_SHA256_ALGORITHM
            );
            Cipher cipher = Cipher.getInstance(cipherAlgorithm);
            if (OAEP_SHA256_ALGORITHM.equals(cipherAlgorithm)) {
                OAEPParameterSpec oaepParameterSpec = new OAEPParameterSpec(
                        "SHA-256",
                        "MGF1",
                        MGF1ParameterSpec.SHA256,
                        PSource.PSpecified.DEFAULT
                );
                cipher.init(Cipher.DECRYPT_MODE, loadPrivateKey(), oaepParameterSpec);
            } else {
                cipher.init(Cipher.DECRYPT_MODE, loadPrivateKey());
            }
            byte[] plainBytes = cipher.doFinal(decodeCiphertext(encryptedToken));
            return new String(plainBytes, StandardCharsets.UTF_8).trim();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Decrypt formal judge token failed", e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "正式判题节点 Token 解密失败");
        }
    }

    /**
     * @MethodName loadPrivateKey
     *
     * @Description 加载私钥
     * @Return @return {@link PrivateKey }
     * @Author HaoRan_Lyu
     * @Date 2026/06/05
     */
    private PrivateKey loadPrivateKey() {
        String privateKeyPem = readPrivateKeyPem();
        try {
            String normalizedPem = privateKeyPem
                    .replace("\\n", "\n")
                    .replace(PRIVATE_KEY_BEGIN, "")
                    .replace(PRIVATE_KEY_END, "")
                    .replace(RSA_PRIVATE_KEY_BEGIN, "")
                    .replace(RSA_PRIVATE_KEY_END, "")
                    .replaceAll("\\s+", "");
            byte[] keyBytes = Base64.getDecoder().decode(normalizedPem);
            return KeyFactory.getInstance(RSA_KEY_ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            log.error("Load formal judge token private key failed", e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "正式判题节点私钥格式错误，请使用 PKCS#8 PEM");
        }
    }

    /**
     * @MethodName readPrivateKeyPem
     *
     * @Description 读取私钥pem
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/06/05
     */
    private String readPrivateKeyPem() {
        String privateKey = StrUtil.trimToNull(formalTokenProperties.getPrivateKey());
        if (privateKey != null) {
            return privateKey;
        }

        String privateKeyPath = StrUtil.trimToNull(formalTokenProperties.getPrivateKeyPath());
        if (privateKeyPath == null) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "正式判题节点私钥未配置");
        }
        try {
            return Files.readString(Path.of(privateKeyPath), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Read formal judge token private key file failed, path: {}", privateKeyPath, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "正式判题节点私钥文件读取失败");
        }
    }

    /**
     * @MethodName decodeCiphertext
     * @Param encryptedToken
     * @Description 解码密文
     * @Return @return {@link byte[] }
     * @Author HaoRan_Lyu
     * @Date 2026/06/05
     */
    private byte[] decodeCiphertext(String encryptedToken) {
        String ciphertext = encryptedToken.trim();
        if (ciphertext.regionMatches(true, 0, RSA_PREFIX, 0, RSA_PREFIX.length())) {
            ciphertext = ciphertext.substring(RSA_PREFIX.length());
        } else if (ciphertext.regionMatches(true, 0, RSA_COLON_PREFIX, 0, RSA_COLON_PREFIX.length())) {
            ciphertext = ciphertext.substring(RSA_COLON_PREFIX.length());
        }
        return Base64.getDecoder().decode(ciphertext.replaceAll("\\s+", ""));
    }

    /**
     * @MethodName constantTimeEquals
     * @Param left
     * @Param right
     * @Description 恒定时间比较
     * @Return @return boolean
     * @Author HaoRan_Lyu
     * @Date 2026/06/05
     */
    private boolean constantTimeEquals(String left, String right) {
        if (right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
