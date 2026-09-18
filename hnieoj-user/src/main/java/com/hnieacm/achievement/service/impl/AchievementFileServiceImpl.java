package com.hnieacm.achievement.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hnieacm.achievement.properties.AchievementFileProperties;
import com.hnieacm.achievement.service.AchievementFileService;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 成就认证文件本地落盘实现
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementFileServiceImpl implements AchievementFileService {

    //TODO: 使用 nacos 管理配置
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

    private final AchievementFileProperties achievementFileProperties;

    /**
     * @MethodName store
     * @Param uid
     * @Param file
     * @Description 商店
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/02/16
     */
    @Override
    public String store(String uid, MultipartFile file) {
        if (StrUtil.isBlank(uid)) {
            throw new BizException(ResultCode.BAD_REQUEST, "uid 不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "file 不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BizException(ResultCode.BAD_REQUEST, "file 过大，最大支持 10MB");
        }

        String uploadDir = resolveUploadDir();
        Path path = Path.of(uploadDir);
        try {
            Files.createDirectories(path);
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "文件目录创建失败");
        }

        String ext = resolveExtension(file.getOriginalFilename());
        String key = uid + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().replace("-", "") + ext;

        Path target = path.resolve(key).normalize();
        try {
            file.transferTo(target);
        } catch (Exception e) {
            log.warn("Store file failed, uid: {}, filename: {}", uid, file.getOriginalFilename(), e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "文件保存失败");
        }

        String prefix = StrUtil.trimToNull(achievementFileProperties.getPublicUrlPrefix());
        if (prefix == null) {
            // 未配置 publicUrlPrefix：返回相对 key，便于运维侧接管 URL 拼接
            return key;
        }
        if (prefix.endsWith("/")) {
            return prefix + key;
        }
        return prefix + "/" + key;
    }

    /**
     * @MethodName resolveUploadDir
     *
     * @Description 解析上传目录
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/02/16
     */
    private String resolveUploadDir() {
        String configured = StrUtil.trimToNull(achievementFileProperties.getUploadDir());
        if (configured != null) {
            return configured;
        }
        String tmp = System.getProperty("java.io.tmpdir");
        String fallback = Path.of(tmp, "hnieoj", "achievement").toString();
        log.warn("hnieoj.achievement.file.upload-dir not configured, fallback to: {}", fallback);
        return fallback;
    }

    /**
     * @MethodName resolveExtension
     * @Param originalFilename
     * @Description 解析扩展名
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/02/16
     */
    private String resolveExtension(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "";
        }
        int idx = originalFilename.lastIndexOf('.');
        if (idx < 0 || idx == originalFilename.length() - 1) {
            return "";
        }
        String ext = originalFilename.substring(idx + 1).trim();
        if (ext.isEmpty()) {
            return "";
        }
        // 只允许字母数字扩展名，避免奇怪字符影响文件系统
        String safe = ext.replaceAll("[^A-Za-z0-9]", "");
        if (safe.isEmpty()) {
            return "";
        }
        return "." + safe.toLowerCase(Locale.ROOT);
    }
}
