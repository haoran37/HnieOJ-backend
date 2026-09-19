package com.hnieacm.judge.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.judge.constant.SystemConfigConstant;
import com.hnieacm.judge.dto.SystemConfigSaveRequest;
import com.hnieacm.judge.dto.TestEmailRequest;
import com.hnieacm.judge.dto.TestEmailSmtpConfigRequest;
import com.hnieacm.judge.entity.SysConfig;
import com.hnieacm.judge.mapper.SysConfigMapper;
import com.hnieacm.judge.service.SystemConfigService;
import com.hnieacm.judge.vo.EmailCheckVo;
import com.hnieacm.judge.vo.SystemClientConfigVo;
import com.hnieacm.judge.vo.SystemConfigVo;
import com.hnieacm.judge.vo.SystemPublicConfigVo;
import com.hnieacm.judge.vo.SystemTimeVo;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

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
    public SystemClientConfigVo getClientConfig() {
        SysConfig config = getOrInitConfig();

        SystemClientConfigVo vo = new SystemClientConfigVo();
        vo.setWebsiteName(config.getWebsiteName());
        vo.setLogoUrl(config.getLogoUrl());
        vo.setIcpCode(config.getIcpCode());
        vo.setSubmissionInterval(config.getSubmissionInterval());
        vo.setRegisterMode(config.getRegisterMode());
        vo.setAllowedEmailSuffixes(parseAllowedEmailSuffixes(config.getAllowedEmailSuffixes()));
        return vo;
    }

    @Override
    public EmailCheckVo checkRegisterEmail(String email) {
        String normalizedEmail = trimToNull(email);
        if (normalizedEmail == null || !normalizedEmail.contains("@")) {
            return buildEmailCheck(false, false, "邮箱格式不正确");
        }

        SysConfig config = getOrInitConfig();
        if (!Boolean.TRUE.equals(config.getAllowRegister())) {
            return buildEmailCheck(false, false, "系统暂未开放注册");
        }

        String registerMode = trimToNull(config.getRegisterMode());
        if (SystemConfigConstant.REGISTER_MODE_OPEN.equals(registerMode)) {
            return buildEmailCheck(true, true, "系统允许开放注册");
        }
        if (!SystemConfigConstant.REGISTER_MODE_EMAIL_SUFFIX.equals(registerMode)) {
            return buildEmailCheck(false, false, "当前注册模式不支持邮箱后缀注册");
        }

        String lowerEmail = normalizedEmail.toLowerCase();
        List<String> suffixes = parseAllowedEmailSuffixes(config.getAllowedEmailSuffixes());
        boolean matched = suffixes.stream()
                .map(String::toLowerCase)
                .anyMatch(lowerEmail::endsWith);
        if (!matched) {
            return buildEmailCheck(false, false, "邮箱后缀不符合系统注册策略");
        }
        return buildEmailCheck(true, true, "邮箱后缀符合系统注册策略");
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

    @Override
    public void sendTestEmail(TestEmailRequest request) {
        if (request == null || request.getSmtpConfig() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "SMTP 配置不能为空");
        }
        String recipient = trimToNull(request.getRecipient());
        if (recipient == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "recipient 不能为空");
        }

        TestEmailSmtpConfigRequest smtpConfig = request.getSmtpConfig();
        try {
            JavaMailSenderImpl mailSender = buildMailSender(smtpConfig);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            String nickname = trimToNull(smtpConfig.getSmtpNickname());
            if (nickname == null) {
                helper.setFrom(smtpConfig.getSmtpEmail());
            } else {
                helper.setFrom(smtpConfig.getSmtpEmail(), nickname);
            }
            helper.setTo(recipient);
            helper.setSubject("HNieOJ 测试邮件");
            helper.setText("这是一封 HNieOJ SMTP 配置测试邮件，收到此邮件表示当前邮件配置可用。", false);
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("Send test email failed, recipient: {}", recipient, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "测试邮件发送失败，请检查 SMTP 配置");
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

    private EmailCheckVo buildEmailCheck(boolean allowRegister, boolean matched, String reason) {
        EmailCheckVo vo = new EmailCheckVo();
        vo.setAllowRegister(allowRegister);
        vo.setMatched(matched);
        vo.setReason(reason);
        return vo;
    }

    private JavaMailSenderImpl buildMailSender(TestEmailSmtpConfigRequest smtpConfig) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(trimToNull(smtpConfig.getSmtpHost()));
        mailSender.setPort(smtpConfig.getSmtpPort());
        mailSender.setUsername(trimToNull(smtpConfig.getSmtpEmail()));
        mailSender.setPassword(trimToNull(smtpConfig.getSmtpPassword()));
        mailSender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        Properties properties = mailSender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.connectiontimeout", "8000");
        properties.put("mail.smtp.timeout", "8000");
        properties.put("mail.smtp.writetimeout", "8000");
        if (Integer.valueOf(465).equals(smtpConfig.getSmtpPort())) {
            properties.put("mail.smtp.ssl.enable", "true");
        } else {
            properties.put("mail.smtp.starttls.enable", "true");
        }
        return mailSender;
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
