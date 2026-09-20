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

import java.io.IOException;
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

    private static final String HTTP_PREFIX = "http://";

    private static final String HTTPS_PREFIX = "https://";

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
     * @MethodName loadLocal
     * @Param storedValue
     * @Description 读取本地附件：仅接受相对 key，拒绝外部 URL 与任何路径穿越，规范化后必须位于 upload-dir 内
     * @Return @return {@link LocalFile }
     * @Author HaoRan_Lyu
     * @Date 2026/09/20
     */
    @Override
    public LocalFile loadLocal(String storedValue) {
        String key = StrUtil.trimToNull(storedValue);
        if (key == null) {
            throw new BizException(ResultCode.NOT_FOUND, "附件不存在");
        }
        String lowerKey = key.toLowerCase(Locale.ROOT);
        if (lowerKey.startsWith(HTTP_PREFIX) || lowerKey.startsWith(HTTPS_PREFIX)) {
            // 外部 URL 由前端直接打开，服务端不做任意地址代理下载
            throw new BizException(ResultCode.BAD_REQUEST, "外部附件地址无需服务端下载");
        }
        if (key.contains("/") || key.contains("\\") || key.contains("..")) {
            throw new BizException(ResultCode.BAD_REQUEST, "附件路径不合法");
        }

        Path uploadRoot = Path.of(resolveUploadDir()).toAbsolutePath().normalize();
        Path target = uploadRoot.resolve(key).normalize();
        if (!target.startsWith(uploadRoot) || !Files.isRegularFile(target)) {
            throw new BizException(ResultCode.NOT_FOUND, "附件不存在");
        }
        try {
            return new LocalFile(Files.readAllBytes(target), target.getFileName().toString());
        } catch (IOException e) {
            log.error("Load achievement file failed, key: {}", key, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "读取附件失败");
        }
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
