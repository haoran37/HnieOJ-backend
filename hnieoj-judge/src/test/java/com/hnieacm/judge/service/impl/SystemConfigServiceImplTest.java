package com.hnieacm.judge.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.judge.constant.SystemConfigConstant;
import com.hnieacm.judge.entity.SysConfig;
import com.hnieacm.judge.mapper.SysConfigMapper;
import com.hnieacm.judge.vo.EmailCheckVo;
import com.hnieacm.judge.vo.SystemClientConfigVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 系统配置服务测试
 */
class SystemConfigServiceImplTest {

    private SysConfigMapper sysConfigMapper;

    private SystemConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        sysConfigMapper = mock(SysConfigMapper.class);
        service = new SystemConfigServiceImpl(sysConfigMapper, new ObjectMapper());
    }

    @Test
    void shouldBuildClientConfigFromSystemConfig() {
        when(sysConfigMapper.selectById(SystemConfigConstant.DEFAULT_CONFIG_ID)).thenReturn(config());

        SystemClientConfigVo vo = service.getClientConfig();

        assertThat(vo.getWebsiteName()).isEqualTo("HNieOJ");
        assertThat(vo.getSubmissionInterval()).isEqualTo(10);
        assertThat(vo.getAllowedEmailSuffixes()).containsExactly("@hnie.edu.cn");
    }

    @Test
    void shouldAllowEmailWhenSuffixMatched() {
        when(sysConfigMapper.selectById(SystemConfigConstant.DEFAULT_CONFIG_ID)).thenReturn(config());

        EmailCheckVo vo = service.checkRegisterEmail("student@hnie.edu.cn");

        assertThat(vo.getAllowRegister()).isTrue();
        assertThat(vo.getMatched()).isTrue();
    }

    @Test
    void shouldRejectEmailWhenSuffixNotMatched() {
        when(sysConfigMapper.selectById(SystemConfigConstant.DEFAULT_CONFIG_ID)).thenReturn(config());

        EmailCheckVo vo = service.checkRegisterEmail("student@example.com");

        assertThat(vo.getAllowRegister()).isFalse();
        assertThat(vo.getMatched()).isFalse();
        assertThat(vo.getReason()).contains("邮箱后缀");
    }

    private SysConfig config() {
        SysConfig config = new SysConfig();
        config.setId(SystemConfigConstant.DEFAULT_CONFIG_ID);
        config.setWebsiteName("HNieOJ");
        config.setSubmissionInterval(10);
        config.setAllowRegister(true);
        config.setRegisterMode(SystemConfigConstant.REGISTER_MODE_EMAIL_SUFFIX);
        config.setAllowedEmailSuffixes("[\"@hnie.edu.cn\"]");
        return config;
    }
}
