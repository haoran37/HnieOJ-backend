package com.hnieacm.judge.vo;

import cn.hutool.core.util.StrUtil;
import com.hnieacm.judge.constant.JudgeNodeConstant;
import com.hnieacm.judge.entity.JudgeNodeToken;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 判题节点 Token 审计展示
 */
@Data
public class JudgeNodeTokenVo {

    private Long id;

    private String tokenId;

    private String nodeId;

    private String nodeName;

    private String nodeType;

    private String status;

    private LocalDateTime expireTime;

    private LocalDateTime lastUsedTime;

    private LocalDateTime lastHeartbeatTime;

    private Boolean online;

    private Integer maxConcurrency;

    private Long runningTasks;

    private Integer cpuCore;

    private String version;

    private List<String> supportedJudgeModes;

    private Long cacheUsedBytes;

    private Integer cacheProblemCount;

    private Long diskTotalBytes;

    private Long diskFreeBytes;

    private LocalDateTime gmtCreate;

    /** 心跳在线判定阈值，与节点安全审计口径保持一致。 */
    private static final long HEARTBEAT_ONLINE_TIMEOUT_SECONDS = 90L;

    /**
     * 将节点持久化实体转换为审计展示 Vo，剥离 DB 凭证/密钥等敏感持久化字段。
     * 复用同一转换，避免 admin 生命周期响应直接序列化实体。
     *
     * @param token 节点注册事实
     * @return 展示 Vo；入参为 null 时返回 null
     */
    public static JudgeNodeTokenVo from(JudgeNodeToken token) {
        if (token == null) {
            return null;
        }
        JudgeNodeTokenVo vo = new JudgeNodeTokenVo();
        vo.setId(token.getId());
        vo.setTokenId(token.getTokenId());
        vo.setNodeId(token.getNodeId());
        vo.setNodeName(token.getNodeName());
        vo.setNodeType(token.getNodeType());
        vo.setStatus(token.getStatus());
        vo.setExpireTime(token.getExpireTime());
        vo.setLastUsedTime(token.getLastUsedTime());
        vo.setLastHeartbeatTime(token.getLastHeartbeatTime());
        vo.setOnline(isOnline(token));
        vo.setMaxConcurrency(token.getMaxConcurrency());
        vo.setRunningTasks(token.getRunningTasks());
        vo.setCpuCore(token.getCpuCore());
        vo.setVersion(token.getVersion());
        vo.setSupportedJudgeModes(splitSupportedJudgeModes(token.getSupportedJudgeModes()));
        vo.setCacheUsedBytes(token.getCacheUsedBytes());
        vo.setCacheProblemCount(token.getCacheProblemCount());
        vo.setDiskTotalBytes(token.getDiskTotalBytes());
        vo.setDiskFreeBytes(token.getDiskFreeBytes());
        vo.setGmtCreate(token.getGmtCreate());
        return vo;
    }

    private static Boolean isOnline(JudgeNodeToken token) {
        if (token.getLastHeartbeatTime() == null || !JudgeNodeConstant.TOKEN_ACTIVE.equals(token.getStatus())) {
            return false;
        }
        return token.getLastHeartbeatTime().isAfter(LocalDateTime.now().minusSeconds(HEARTBEAT_ONLINE_TIMEOUT_SECONDS));
    }

    private static List<String> splitSupportedJudgeModes(String supportedJudgeModes) {
        String normalizedModes = StrUtil.blankToDefault(supportedJudgeModes, "default");
        return Arrays.stream(normalizedModes.split(","))
                .map(StrUtil::trimToNull)
                .filter(item -> item != null)
                .toList();
    }
}
