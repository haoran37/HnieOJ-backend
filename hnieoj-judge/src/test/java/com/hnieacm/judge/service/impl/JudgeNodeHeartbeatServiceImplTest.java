package com.hnieacm.judge.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.hnieacm.judge.constant.JudgeNodeConstant;
import com.hnieacm.judge.entity.JudgeNodeToken;
import com.hnieacm.judge.mapper.JudgeNodeTokenMapper;
import com.hnieacm.judge.properties.JudgeSecurityProperties;
import com.hnieacm.judge.service.JudgeNodeSecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/08
 * @Description: 判题节点心跳能力查询测试
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class JudgeNodeHeartbeatServiceImplTest {

    @Mock
    private JudgeNodeSecurityService judgeNodeSecurityService;

    @Mock
    private JudgeNodeTokenMapper judgeNodeTokenMapper;

    private JudgeNodeHeartbeatServiceImpl service;

    @BeforeEach
    void setUp() {
        JudgeSecurityProperties properties = new JudgeSecurityProperties();
        properties.setNodeActiveTimeoutSeconds(90);
        service = new JudgeNodeHeartbeatServiceImpl(judgeNodeSecurityService, judgeNodeTokenMapper, properties);
    }

    @Test
    void shouldFindActiveNodeBySupportedJudgeMode() {
        JudgeNodeToken token = new JudgeNodeToken();
        token.setStatus(JudgeNodeConstant.TOKEN_ACTIVE);
        token.setExpireTime(LocalDateTime.now().plusMinutes(10));
        token.setLastHeartbeatTime(LocalDateTime.now());
        token.setSupportedJudgeModes("default,spj");
        when(judgeNodeTokenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(token));

        assertThat(service.hasActiveNodeForMode("spj")).isTrue();
        assertThat(service.hasActiveNodeForMode("interactive")).isFalse();
    }
}
