package com.hnieacm.judge.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.judge.NodeProtocolConstants;
import com.hnieacm.judge.entity.JudgeNodeToken;
import com.hnieacm.judge.mapper.JudgeNodeTokenMapper;
import com.hnieacm.judge.vo.JudgeNodeTokenVo;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 生命周期管理回归：普通 drain 不提升 accessVersion，disable/enable 提升，
 * 吊销/硬到期不可由 enable 复活。
 *
 * @author Codex
 */
@ExtendWith(MockitoExtension.class)
class JudgeNodeLifecycleServiceImplTest {

    @Mock
    private JudgeNodeTokenMapper tokenMapper;

    private JudgeNodeLifecycleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new JudgeNodeLifecycleServiceImpl(tokenMapper);
        // 纯 Mockito 单测不启动 Spring，需显式初始化实体的 MyBatis-Plus 表元数据，
        // 否则 LambdaUpdateWrapper 解析列名时会因 lambda cache 缺失而报错。
        initTableInfo(JudgeNodeToken.class);
    }

    @Test
    void drainDoesNotBumpAccessVersion() {
        JudgeNodeToken node = node(NodeProtocolConstants.NODE_STATUS_ACTIVE, 3, null);
        when(tokenMapper.lockNode("n1")).thenReturn(node);
        when(tokenMapper.selectById(1L)).thenReturn(node);

        JudgeNodeTokenVo vo = service.drain("n1");

        // admin 生命周期响应必须是展示 Vo（不直接序列化 DB 凭证实体）。
        assertThat(vo).isNotNull();
        assertThat(vo.getTokenId()).isEqualTo("n1");
        String sqlSet = captureUpdateSqlSet();
        assertThat(sqlSet).contains("status", "draining");
        assertThat(sqlSet).doesNotContain("access_version");
    }

    @Test
    void disableBumpsAccessVersion() {
        JudgeNodeToken node = node(NodeProtocolConstants.NODE_STATUS_ACTIVE, 3, null);
        when(tokenMapper.lockNode("n1")).thenReturn(node);
        when(tokenMapper.selectById(1L)).thenReturn(node);

        service.disable("n1");

        assertThat(captureUpdateSqlSet()).contains("access_version");
    }

    @Test
    void enableBumpsAccessVersion() {
        JudgeNodeToken node = node(NodeProtocolConstants.NODE_STATUS_DISABLED, 4, null);
        when(tokenMapper.lockNode("n1")).thenReturn(node);
        when(tokenMapper.selectById(1L)).thenReturn(node);

        service.enable("n1");

        assertThat(captureUpdateSqlSet()).contains("access_version");
    }

    @Test
    void enableCannotResurrectRevokedNode() {
        JudgeNodeToken node = node(NodeProtocolConstants.NODE_STATUS_REVOKED, 9, null);
        when(tokenMapper.lockNode("n1")).thenReturn(node);

        assertThatThrownBy(() -> service.enable("n1")).isInstanceOf(BizException.class);
        verify(tokenMapper, never()).update(any(), any());
    }

    @Test
    void enableCannotReviveHardExpiredNode() {
        JudgeNodeToken node = node(NodeProtocolConstants.NODE_STATUS_DISABLED, 2,
                LocalDateTime.now().minusMinutes(1));
        when(tokenMapper.lockNode("n1")).thenReturn(node);

        assertThatThrownBy(() -> service.enable("n1")).isInstanceOf(BizException.class);
        verify(tokenMapper, never()).update(any(), any());
    }

    @SuppressWarnings("unchecked")
    private String captureUpdateSqlSet() {
        ArgumentCaptor<LambdaUpdateWrapper<JudgeNodeToken>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(tokenMapper).update(isNull(), captor.capture());
        return captor.getValue().getSqlSet();
    }

    private void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }

    private JudgeNodeToken node(String status, int accessVersion, LocalDateTime authorizationUntil) {
        JudgeNodeToken node = new JudgeNodeToken();
        node.setId(1L);
        node.setTokenId("n1");
        node.setNodeId("n1");
        node.setStatus(status);
        node.setAccessVersion(accessVersion);
        node.setWeight(10);
        node.setMaxConcurrency(1);
        node.setDraining(false);
        node.setExpireTime(LocalDateTime.now().plusDays(1));
        node.setAuthorizationUntil(authorizationUntil);
        node.setSupportedJudgeModes("default");
        return node;
    }
}
