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
import com.hnieacm.judge.vo.JudgeTokenResetVo;
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
import java.util.UUID;

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

    private static final int TOKEN_MASK_PREFIX_LENGTH = 4;

    private final SysConfigMapper sysConfigMapper;
    private final ObjectMapper objectMapper;

    /**
     * @MethodName getPublicConfig
     * <p>
     * @Description 获取公共配置
     * @Return @return {@link SystemPublicConfigVo }
     * @Author HaoRan_Lyu
     * @Date 2026/03/01
     */
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

    /**
     * @MethodName getSystemTime
     * <p>
     * @Description 获取系统时间
     * @Return @return {@link SystemTimeVo }
     * @Author HaoRan_Lyu
     * @Date 2026/03/01
     */
    @Override
    public SystemTimeVo getSystemTime() {
        ZonedDateTime now = ZonedDateTime.now();
        SystemTimeVo vo = new SystemTimeVo();
        vo.setServerTime(now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        vo.setTimezone(now.getZone().getId());
        vo.setUnixTimestamp(now.toInstant().toEpochMilli());
        return vo;
    }

    /**
     * @MethodName getSystemConfig
     * <p>
     * @Description 获取系统配置
     * @Return @return {@link SystemConfigVo }
     * @Author HaoRan_Lyu
     * @Date 2026/03/01
     */
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
        vo.setJudgeToken(maskJudgeToken(config.getJudgeToken()));
        vo.setSubmissionInterval(config.getSubmissionInterval());
        vo.setGmtModified(config.getGmtModified());
        return vo;
    }

    /**
     * @MethodName saveSystemConfig
     * @Param request
     * @Description 保存系统配置
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/03/01
     */
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

    /**
     * @MethodName resetJudgeToken
     *
     * @Description 重置判题令牌
     * @Return @return {@link JudgeTokenResetVo }
     * @Author HaoRan_Lyu
     * @Date 2026/03/01
     */
    @Override
    public JudgeTokenResetVo resetJudgeToken() {
        getOrInitConfig();

        String newToken = UUID.randomUUID().toString().replace("-", "");
        LambdaUpdateWrapper<SysConfig> updateWrapper = new LambdaUpdateWrapper<SysConfig>()
                .eq(SysConfig::getId, SystemConfigConstant.DEFAULT_CONFIG_ID)
                .set(SysConfig::getJudgeToken, newToken);

        int updated = sysConfigMapper.update(null, updateWrapper);
        if (updated <= 0) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "重置 Judger Token 失败");
        }

        JudgeTokenResetVo vo = new JudgeTokenResetVo();
        vo.setToken(newToken);
        return vo;
    }

    /**
     * @MethodName getOrInitConfig
     *
     * @Description 统一兜底初始化 id=1 配置，保证后续逻辑可直接读取
     * @Return @return {@link SysConfig }
     * @Author HaoRan_Lyu
     * @Date 2026/03/01
     */
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

    /**
     * @MethodName validateRegisterMode
     * @Param registerMode
     * @Description 验证注册模式
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/03/01
     */
    private void validateRegisterMode(String registerMode) {
        if (!SystemConfigConstant.REGISTER_MODE_SET.contains(registerMode)) {
            throw new BizException(
                    ResultCode.BAD_REQUEST,
                    "registerMode 仅支持 " + SystemConfigConstant.REGISTER_MODE_SET
            );
        }
    }

    /**
     * @MethodName parseAllowedEmailSuffixes
     * @Param rawJson
     * @Description 解析允许电子邮件后缀
     * @Return @return {@link List }<{@link String }>
     * @Author HaoRan_Lyu
     * @Date 2026/03/01
     */
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

    /**
     * @MethodName toAllowedEmailSuffixesJson
     * @Param suffixes
     * @Description 允许使用json电子邮件后缀
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/03/01
     */
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

    /**
     * @MethodName resolveSmtpPassword
     * @Param currentPassword
     * @Param requestPassword
     * @Description 解析smtp密码
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/03/01
     */
    private String resolveSmtpPassword(String currentPassword, String requestPassword) {
        if (requestPassword == null) {
            return currentPassword;
        }
        return trimToNull(requestPassword);
    }

    /**
     * @MethodName maskJudgeToken
     * @Param token
     * @Description mask裁判令牌
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/03/01
     */
    private String maskJudgeToken(String token) {
        if (StrUtil.isBlank(token)) {
            return null;
        }
        if (token.length() <= TOKEN_MASK_PREFIX_LENGTH) {
            return "****";
        }
        return token.substring(0, TOKEN_MASK_PREFIX_LENGTH) + "****";
    }

    /**
     * @MethodName trimToNull
     * @Param value
     * @Description 去除字符串首尾空格，若结果为空则返回null
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/03/01
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
