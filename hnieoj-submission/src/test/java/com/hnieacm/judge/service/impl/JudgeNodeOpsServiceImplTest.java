package com.hnieacm.judge.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.hnieacm.judge.constant.JudgeNodeConstant;
import com.hnieacm.judge.entity.JudgeNodeToken;
import com.hnieacm.judge.mapper.JudgeNodeTokenMapper;
import com.hnieacm.judge.properties.JudgeSecurityProperties;
import com.hnieacm.judge.vo.JudgeNodeOpsSummaryVo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: Judge node operation summary tests.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class JudgeNodeOpsServiceImplTest {

    @Test
    void shouldBuildNodeOpsSummaryFromHeartbeatRecords() {
        JudgeNodeTokenMapper tokenMapper = mock(JudgeNodeTokenMapper.class);
        JudgeSecurityProperties properties = new JudgeSecurityProperties();
        properties.setNodeActiveTimeoutSeconds(90);
        properties.setNodeExpireWarnSeconds(1800);
        properties.setNodeDiskFreeWarnRatio(0.1D);
        when(tokenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                onlineFormalNode(),
                offlineTempNode(),
                overloadedLowDiskNode(),
                revokedTempNode()
        ));
        JudgeNodeOpsServiceImpl service = new JudgeNodeOpsServiceImpl(tokenMapper, properties);

        JudgeNodeOpsSummaryVo summary = service.summary();

        assertThat(summary.getTotalNodeCount()).isEqualTo(4L);
        assertThat(summary.getActiveNodeCount()).isEqualTo(3L);
        assertThat(summary.getOnlineNodeCount()).isEqualTo(2L);
        assertThat(summary.getOfflineActiveNodeCount()).isEqualTo(1L);
        assertThat(summary.getFormalNodeCount()).isEqualTo(1L);
        assertThat(summary.getTempNodeCount()).isEqualTo(3L);
        assertThat(summary.getRevokedNodeCount()).isEqualTo(1L);
        assertThat(summary.getTempTokenExpiringSoonCount()).isEqualTo(1L);
        assertThat(summary.getOverloadedNodeCount()).isEqualTo(1L);
        assertThat(summary.getLowDiskNodeCount()).isEqualTo(1L);
        assertThat(summary.getTotalMaxConcurrency()).isEqualTo(6L);
        assertThat(summary.getTotalRunningTasks()).isEqualTo(5L);
        assertThat(summary.getJudgeModeOnlineCounts().get("default")).isEqualTo(1L);
        assertThat(summary.getJudgeModeOnlineCounts().get("spj")).isEqualTo(1L);
        assertThat(summary.getJudgeModeOnlineCounts().get("interactive")).isEqualTo(1L);
        assertThat(summary.getHealthy()).isFalse();
        assertThat(summary.getWarnings()).contains(
                "offline_active_judge_node",
                "overloaded_judge_node",
                "low_disk_judge_node",
                "temp_token_expiring"
        );
    }

    private JudgeNodeToken onlineFormalNode() {
        JudgeNodeToken node = new JudgeNodeToken();
        node.setNodeType(JudgeNodeConstant.NODE_TYPE_FORMAL);
        node.setStatus(JudgeNodeConstant.TOKEN_ACTIVE);
        node.setLastHeartbeatTime(LocalDateTime.now());
        node.setMaxConcurrency(4);
        node.setRunningTasks(1L);
        node.setSupportedJudgeModes("default,spj");
        node.setDiskTotalBytes(1000L);
        node.setDiskFreeBytes(500L);
        return node;
    }

    private JudgeNodeToken offlineTempNode() {
        JudgeNodeToken node = new JudgeNodeToken();
        node.setNodeType(JudgeNodeConstant.NODE_TYPE_TEMP);
        node.setStatus(JudgeNodeConstant.TOKEN_ACTIVE);
        node.setLastHeartbeatTime(LocalDateTime.now().minusMinutes(10));
        node.setExpireTime(LocalDateTime.now().plusMinutes(20));
        return node;
    }

    private JudgeNodeToken overloadedLowDiskNode() {
        JudgeNodeToken node = new JudgeNodeToken();
        node.setNodeType(JudgeNodeConstant.NODE_TYPE_TEMP);
        node.setStatus(JudgeNodeConstant.TOKEN_ACTIVE);
        node.setLastHeartbeatTime(LocalDateTime.now());
        node.setMaxConcurrency(2);
        node.setRunningTasks(4L);
        node.setSupportedJudgeModes("interactive");
        node.setDiskTotalBytes(1000L);
        node.setDiskFreeBytes(50L);
        return node;
    }

    private JudgeNodeToken revokedTempNode() {
        JudgeNodeToken node = new JudgeNodeToken();
        node.setNodeType(JudgeNodeConstant.NODE_TYPE_TEMP);
        node.setStatus(JudgeNodeConstant.TOKEN_REVOKED);
        return node;
    }
}
