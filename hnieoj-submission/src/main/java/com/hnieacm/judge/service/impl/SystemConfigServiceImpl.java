package com.hnieacm.judge.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.judge.constant.SystemConfigConstant;
import com.hnieacm.judge.dto.SystemConfigSaveRequest;
import com.hnieacm.judge.entity.SysConfig;
import com.hnieacm.judge.mapper.SysConfigMapper;
import com.hnieacm.judge.service.SystemConfigService;
import com.hnieacm.judge.vo.SystemConfigVo;
import com.hnieacm.judge.vo.SystemPublicConfigVo;
import com.hnieacm.judge.vo.SystemTimeVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/03/01
 * @Description: 系统配置服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    private static final TypeReference<List<String>> LIST_STRING_TYPE = new TypeReference<>() {
    };

    private final SysConfigMapper sysConfigMapper;
    private final ObjectMapper objectMapper;

    @Override
    public SystemPublicConfigVo getPublicConfig() {
        SysConfig config = getOrInitConfig();

        SystemPublicConfigVo vo = new SystemPublicConfigVo();
        vo.setWebsiteName(config.getWebsiteName());
        vo.setLogoUrl(config.getLogoUrl());
        vo.setIcpCode(config.getIcpCode());
        vo.setAllowRegister(config.getAllowRegister());
        vo.setRegisterMode(config.getRegisterMode());
        vo.setAllowedEmailSuffixes(parseAllowedEmailSuffixes(config.getAllowedEmailSuffixes()));
        vo.setGmtModified(config.getGmtModified());
        return vo;
    }

    @Override
    public SystemTimeVo getSystemTime() {
        ZonedDateTime now = ZonedDateTime.now();
        SystemTimeVo vo = new SystemTimeVo();
        vo.setServerTime(now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        vo.setTimezone(now.getZone().getId());
        vo.setUnixTimestamp(now.toInstant().toEpochMilli());
        return vo;
    }

    @Override
    public SystemConfigVo getSystemConfig() {
        SysConfig config = getOrInitConfig();

        SystemConfigVo vo = new SystemConfigVo();
        vo.setWebsiteName(config.getWebsiteName());
        vo.setLogoUrl(config.getLogoUrl());
        vo.setIcpCode(config.getIcpCode());
        vo.setAllowRegister(config.getAllowRegister());
        vo.setRegisterMode(config.getRegisterMode());
        vo.setAllowedEmailSuffixes(parseAllowedEmailSuffixes(config.getAllowedEmailSuffixes()));
        vo.setSmtpHost(config.getSmtpHost());
        vo.setSmtpPort(config.getSmtpPort());
        vo.setSmtpEmail(config.getSmtpEmail());
        vo.setSmtpNickname(config.getSmtpNickname());
        vo.setSubmissionInterval(config.getSubmissionInterval());
        vo.setGmtModified(config.getGmtModified());
        return vo;
    }

    @Override
    public void saveSystemConfig(SystemConfigSaveRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        String websiteName = trimToNull(request.getWebsiteName());
        if (websiteName == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "websiteName 不能为空");
        }

        String registerMode = trimToNull(request.getRegisterMode());
        if (registerMode == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "registerMode 不能为空");
        }
        validateRegisterMode(registerMode);

        Integer submissionInterval = request.getSubmissionInterval();
        if (submissionInterval == null || submissionInterval < SystemConfigConstant.MIN_SUBMISSION_INTERVAL) {
            throw new BizException(ResultCode.BAD_REQUEST, "submissionInterval 最小为 1");
        }
        if (request.getAllowRegister() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "allowRegister 不能为空");
        }

        SysConfig currentConfig = getOrInitConfig();
        String resolvedPassword = resolveSmtpPassword(currentConfig.getSmtpPassword(), request.getSmtpPassword());
        String allowedEmailSuffixesJson = toAllowedEmailSuffixesJson(request.getAllowedEmailSuffixes());

        LambdaUpdateWrapper<SysConfig> updateWrapper = new LambdaUpdateWrapper<SysConfig>()
                .eq(SysConfig::getId, SystemConfigConstant.DEFAULT_CONFIG_ID)
                .set(SysConfig::getWebsiteName, websiteName)
                .set(SysConfig::getLogoUrl, trimToNull(request.getLogoUrl()))
                .set(SysConfig::getIcpCode, trimToNull(request.getIcpCode()))
                .set(SysConfig::getAllowRegister, request.getAllowRegister())
                .set(SysConfig::getRegisterMode, registerMode)
                .set(SysConfig::getAllowedEmailSuffixes, allowedEmailSuffixesJson)
                .set(SysConfig::getSmtpHost, trimToNull(request.getSmtpHost()))
                .set(SysConfig::getSmtpPort, request.getSmtpPort())
                .set(SysConfig::getSmtpEmail, trimToNull(request.getSmtpEmail()))
                .set(SysConfig::getSmtpPassword, resolvedPassword)
                .set(SysConfig::getSmtpNickname, trimToNull(request.getSmtpNickname()))
                .set(SysConfig::getSubmissionInterval, submissionInterval);

        int updated = sysConfigMapper.update(null, updateWrapper);
        if (updated <= 0) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "配置保存失败");
        }
    }

    private SysConfig getOrInitConfig() {
        SysConfig config = sysConfigMapper.selectById(SystemConfigConstant.DEFAULT_CONFIG_ID);
        if (config != null) {
            return config;
        }

        SysConfig initialConfig = new SysConfig();
        initialConfig.setId(SystemConfigConstant.DEFAULT_CONFIG_ID);
        try {
            sysConfigMapper.insert(initialConfig);
        } catch (Exception e) {
            log.warn("Init system config failed, id: {}", SystemConfigConstant.DEFAULT_CONFIG_ID, e);
        }

        config = sysConfigMapper.selectById(SystemConfigConstant.DEFAULT_CONFIG_ID);
        if (config == null) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "系统配置不存在");
        }
        return config;
    }

    private void validateRegisterMode(String registerMode) {
        if (!SystemConfigConstant.REGISTER_MODE_SET.contains(registerMode)) {
            throw new BizException(
                    ResultCode.BAD_REQUEST,
                    "registerMode 仅支持 " + SystemConfigConstant.REGISTER_MODE_SET
            );
        }
    }

    private List<String> parseAllowedEmailSuffixes(String rawJson) {
        if (StrUtil.isBlank(rawJson)) {
            return Collections.emptyList();
        }
        try {
            List<String> suffixes = objectMapper.readValue(rawJson, LIST_STRING_TYPE);
            if (suffixes == null) {
                return Collections.emptyList();
            }
            return suffixes.stream()
                    .map(this::trimToNull)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        } catch (Exception e) {
            log.warn("Parse allowedEmailSuffixes failed, value: {}", rawJson, e);
            return Collections.emptyList();
        }
    }

    private String toAllowedEmailSuffixesJson(List<String> suffixes) {
        List<String> normalized = suffixes == null ? Collections.emptyList() : suffixes.stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            throw new BizException(ResultCode.BAD_REQUEST, "allowedEmailSuffixes 格式不合法");
        }
    }

    private String resolveSmtpPassword(String currentPassword, String requestPassword) {
        if (requestPassword == null) {
            return currentPassword;
        }
        return trimToNull(requestPassword);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
