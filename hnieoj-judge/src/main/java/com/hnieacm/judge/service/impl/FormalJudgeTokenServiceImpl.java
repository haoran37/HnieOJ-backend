package com.hnieacm.judge.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.ConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.judge.constant.JudgeNodeConstant;
import com.hnieacm.judge.entity.JudgeFormalToken;
import com.hnieacm.judge.mapper.JudgeFormalTokenMapper;
import com.hnieacm.judge.properties.JudgeFormalTokenProperties;
import com.hnieacm.judge.service.FormalJudgeTokenService;
import com.hnieacm.judge.vo.JudgeFormalTokenVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 正式判题节点长期 Token 服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormalJudgeTokenServiceImpl implements FormalJudgeTokenService {

    private static final int FORMAL_TOKEN_RANDOM_BYTES = 48;
    private static final String RSA_PREFIX = "{rsa}";
    private static final String PUBLIC_KEY_BEGIN = "-----BEGIN PUBLIC KEY-----";
    private static final String PUBLIC_KEY_END = "-----END PUBLIC KEY-----";
    private static final String RSA_KEY_ALGORITHM = "RSA";
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String OAEP_SHA256_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String NACOS_TEMPLATE = """
            hnieoj:
              judge:
                formal-token:
                  encrypted-token: "%s"
                  version: %d
                  updated-at: "%s"
            """;

    private final JudgeFormalTokenMapper formalTokenMapper;
    private final JudgeFormalTokenProperties formalTokenProperties;
    private final NacosConfigManager nacosConfigManager;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public boolean matches(String rawToken) {
        String normalizedToken = StrUtil.trimToNull(rawToken);
        if (normalizedToken == null) {
            return false;
        }

        JudgeFormalToken activeToken = formalTokenMapper.selectOne(new LambdaQueryWrapper<JudgeFormalToken>()
                .eq(JudgeFormalToken::getStatus, JudgeNodeConstant.FORMAL_TOKEN_ACTIVE)
                .orderByDesc(JudgeFormalToken::getVersion)
                .last("limit 1"));
        if (activeToken == null || StrUtil.isBlank(activeToken.getTokenHash())) {
            log.warn("Formal judge token hash is not initialized");
            return false;
        }
        String requestHash = hash(normalizedToken);
        return MessageDigest.isEqual(
                requestHash.getBytes(StandardCharsets.UTF_8),
                activeToken.getTokenHash().getBytes(StandardCharsets.UTF_8)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JudgeFormalTokenVo rotate() {
        return rotateInternal(StpUtil.getLoginIdAsString(), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JudgeFormalTokenVo initializeIfNecessary() {
        JudgeFormalToken activeToken = findActiveToken();
        if (activeToken != null) {
            return toVo(activeToken);
        }
        return rotateInternal("system", false);
    }

    private JudgeFormalTokenVo rotateInternal(String operator, boolean rotateExisting) {
        String rawToken = generateToken();
        int nextVersion = resolveNextVersion();
        String encryptedToken = encryptToken(rawToken);

        if (rotateExisting) {
            formalTokenMapper.update(null, new LambdaUpdateWrapper<JudgeFormalToken>()
                    .eq(JudgeFormalToken::getStatus, JudgeNodeConstant.FORMAL_TOKEN_ACTIVE)
                    .set(JudgeFormalToken::getStatus, JudgeNodeConstant.FORMAL_TOKEN_ROTATED));
        }

        JudgeFormalToken tokenRecord = new JudgeFormalToken();
        tokenRecord.setVersion(nextVersion);
        tokenRecord.setTokenHash(hash(rawToken));
        tokenRecord.setHashAlgorithm(HASH_ALGORITHM);
        tokenRecord.setEncryptedToken(encryptedToken);
        tokenRecord.setStatus(JudgeNodeConstant.FORMAL_TOKEN_ACTIVE);
        tokenRecord.setRotatedBy(operator);
        formalTokenMapper.insert(tokenRecord);

        publishEncryptedToken(encryptedToken, nextVersion);
        log.info("Formal judge token rotated, version: {}", nextVersion);
        return toVo(tokenRecord);
    }

    private JudgeFormalToken findActiveToken() {
        return formalTokenMapper.selectOne(new LambdaQueryWrapper<JudgeFormalToken>()
                .eq(JudgeFormalToken::getStatus, JudgeNodeConstant.FORMAL_TOKEN_ACTIVE)
                .orderByDesc(JudgeFormalToken::getVersion)
                .last("limit 1"));
    }

    private int resolveNextVersion() {
        JudgeFormalToken latestToken = formalTokenMapper.selectOne(new LambdaQueryWrapper<JudgeFormalToken>()
                .orderByDesc(JudgeFormalToken::getVersion)
                .last("limit 1"));
        if (latestToken == null || latestToken.getVersion() == null) {
            return 1;
        }
        return latestToken.getVersion() + 1;
    }

    private String generateToken() {
        byte[] bytes = new byte[FORMAL_TOKEN_RANDOM_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        return SecureUtil.sha256(value);
    }

    private String encryptToken(String rawToken) {
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
                cipher.init(Cipher.ENCRYPT_MODE, loadPublicKey(), oaepParameterSpec);
            } else {
                cipher.init(Cipher.ENCRYPT_MODE, loadPublicKey());
            }
            byte[] cipherBytes = cipher.doFinal(rawToken.getBytes(StandardCharsets.UTF_8));
            return RSA_PREFIX + Base64.getEncoder().encodeToString(cipherBytes);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Encrypt formal judge token failed", e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "正式判题节点 Token 加密失败");
        }
    }

    private PublicKey loadPublicKey() {
        String publicKeyPem = readPublicKeyPem();
        try {
            String normalizedPem = publicKeyPem
                    .replace("\\n", "\n")
                    .replace(PUBLIC_KEY_BEGIN, "")
                    .replace(PUBLIC_KEY_END, "")
                    .replaceAll("\\s+", "");
            byte[] keyBytes = Base64.getDecoder().decode(normalizedPem);
            return KeyFactory.getInstance(RSA_KEY_ALGORITHM).generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            log.error("Load formal judge token public key failed", e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "正式判题节点公钥格式错误，请使用 X.509 PEM 公钥");
        }
    }

    private String readPublicKeyPem() {
        String publicKey = StrUtil.trimToNull(formalTokenProperties.getPublicKey());
        if (publicKey != null) {
            return publicKey;
        }

        String publicKeyPath = StrUtil.trimToNull(formalTokenProperties.getPublicKeyPath());
        if (publicKeyPath == null) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "正式判题节点公钥未配置");
        }
        try {
            return Files.readString(Path.of(publicKeyPath), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Read formal judge token public key file failed, path: {}", publicKeyPath, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "正式判题节点公钥文件读取失败");
        }
    }

    private void publishEncryptedToken(String encryptedToken, int version) {
        String dataId = StrUtil.trimToNull(formalTokenProperties.getNacosDataId());
        String group = StrUtil.trimToNull(formalTokenProperties.getNacosGroup());
        if (dataId == null || group == null) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "正式判题节点 Token 的 Nacos 发布目标未配置");
        }

        String updatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String content = NACOS_TEMPLATE.formatted(encryptedToken, version, updatedAt);
        try {
            ConfigService configService = nacosConfigManager.getConfigService();
            boolean success = configService.publishConfig(dataId, group, content);
            if (!success) {
                throw new BizException(ResultCode.INTERNAL_ERROR, "正式判题节点 Token 发布到 Nacos 失败");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Publish formal judge token to Nacos failed, dataId: {}, group: {}", dataId, group, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "正式判题节点 Token 发布到 Nacos 失败");
        }
    }

    private JudgeFormalTokenVo toVo(JudgeFormalToken tokenRecord) {
        JudgeFormalTokenVo vo = new JudgeFormalTokenVo();
        vo.setId(tokenRecord.getId());
        vo.setVersion(tokenRecord.getVersion());
        vo.setStatus(tokenRecord.getStatus());
        vo.setNacosDataId(formalTokenProperties.getNacosDataId());
        vo.setNacosGroup(formalTokenProperties.getNacosGroup());
        vo.setRotatedTime(tokenRecord.getGmtCreate() == null ? LocalDateTime.now() : tokenRecord.getGmtCreate());
        return vo;
    }
}
