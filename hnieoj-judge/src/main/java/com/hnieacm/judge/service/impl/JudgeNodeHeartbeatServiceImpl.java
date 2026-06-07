package com.hnieacm.judge.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.judge.constant.JudgeNodeConstant;
import com.hnieacm.judge.dto.JudgeNodeHeartbeatRequest;
import com.hnieacm.judge.dto.ValidateJudgeNodeTokenRequest;
import com.hnieacm.judge.entity.JudgeNodeToken;
import com.hnieacm.judge.mapper.JudgeNodeTokenMapper;
import com.hnieacm.judge.service.JudgeNodeHeartbeatService;
import com.hnieacm.judge.service.JudgeNodeSecurityService;
import com.hnieacm.judge.vo.JudgeNodeTokenValidationVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 判题节点心跳服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeNodeHeartbeatServiceImpl implements JudgeNodeHeartbeatService {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final int FORMAL_TOKEN_ID_HASH_LENGTH = 32;
    private static final int MAX_RUNNING_TASKS = 100000;
    private static final int MAX_CONCURRENCY = 10000;
    private static final int MAX_CPU_CORE = 4096;

    private final JudgeNodeSecurityService judgeNodeSecurityService;
    private final JudgeNodeTokenMapper judgeNodeTokenMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordHeartbeat(String judgeToken, String authorizationHeader, JudgeNodeHeartbeatRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "心跳请求不能为空");
        }
        JudgeNodeTokenValidationVo validation = validateAccess(judgeToken, authorizationHeader);
        if (!Boolean.TRUE.equals(validation.getValid())) {
            throw new BizException(ResultCode.FORBIDDEN, "判题节点凭证无效");
        }

        validateRequest(request);
        if (JudgeNodeConstant.NODE_TYPE_FORMAL.equals(validation.getNodeType())) {
            recordFormalHeartbeat(request);
            return;
        }
        recordTempHeartbeat(validation, request);
    }

    private JudgeNodeTokenValidationVo validateAccess(String judgeToken, String authorizationHeader) {
        ValidateJudgeNodeTokenRequest validateRequest = new ValidateJudgeNodeTokenRequest();
        validateRequest.setJudgeToken(StrUtil.trimToNull(judgeToken));
        validateRequest.setBearerToken(parseBearerToken(authorizationHeader));
        return judgeNodeSecurityService.validateToken(validateRequest);
    }

    private void validateRequest(JudgeNodeHeartbeatRequest request) {
        request.setNodeId(StrUtil.trimToNull(request.getNodeId()));
        request.setNodeName(StrUtil.trimToNull(request.getNodeName()));
        request.setNodeType(StrUtil.trimToNull(request.getNodeType()));
        request.setVersion(StrUtil.trimToNull(request.getVersion()));
        if (request.getNodeId() == null) {
            request.setNodeId(request.getNodeName());
        }
        if (request.getNodeId() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "nodeId 不能为空");
        }
        if (!JudgeNodeConstant.NODE_TYPE_FORMAL.equals(request.getNodeType())
                && !JudgeNodeConstant.NODE_TYPE_TEMP.equals(request.getNodeType())) {
            throw new BizException(ResultCode.BAD_REQUEST, "nodeType 不合法");
        }
        validateRange(request.getMaxConcurrency(), "maxConcurrency", MAX_CONCURRENCY);
        validateRange(request.getRunningTasks(), "runningTasks", MAX_RUNNING_TASKS);
        validateRange(request.getCpuCore(), "cpuCore", MAX_CPU_CORE);
    }

    private void recordFormalHeartbeat(JudgeNodeHeartbeatRequest request) {
        if (!JudgeNodeConstant.NODE_TYPE_FORMAL.equals(request.getNodeType())) {
            throw new BizException(ResultCode.FORBIDDEN, "正式节点类型不一致");
        }
        String tokenId = formalTokenId(request.getNodeId());
        JudgeNodeToken exists = judgeNodeTokenMapper.selectOne(new LambdaQueryWrapper<JudgeNodeToken>()
                .eq(JudgeNodeToken::getTokenId, tokenId)
                .last("limit 1"));
        if (exists == null) {
            JudgeNodeToken token = new JudgeNodeToken();
            token.setTokenId(tokenId);
            token.setNodeId(request.getNodeId());
            token.setNodeName(request.getNodeName());
            token.setNodeType(JudgeNodeConstant.NODE_TYPE_FORMAL);
            token.setStatus(JudgeNodeConstant.TOKEN_ACTIVE);
            token.setExpireTime(LocalDateTime.now().plusYears(100));
            fillHeartbeat(token, request);
            judgeNodeTokenMapper.insert(token);
            log.info("Formal judge node heartbeat registered, nodeId: {}", request.getNodeId());
            return;
        }
        updateHeartbeat(exists.getId(), request);
        log.debug("Formal judge node heartbeat updated, nodeId: {}", request.getNodeId());
    }

    private void recordTempHeartbeat(JudgeNodeTokenValidationVo validation, JudgeNodeHeartbeatRequest request) {
        if (!JudgeNodeConstant.NODE_TYPE_TEMP.equals(request.getNodeType())) {
            throw new BizException(ResultCode.FORBIDDEN, "临时节点类型不一致");
        }
        if (!request.getNodeId().equals(validation.getNodeId())) {
            throw new BizException(ResultCode.FORBIDDEN, "临时节点 ID 不一致");
        }
        int updated = updateHeartbeatByTokenId(validation.getTokenId(), request);
        if (updated <= 0) {
            throw new BizException(ResultCode.FORBIDDEN, "临时节点 Token 不可用");
        }
        log.debug("Temp judge node heartbeat updated, nodeId: {}, tokenId: {}",
                validation.getNodeId(), validation.getTokenId());
    }

    private int updateHeartbeatByTokenId(String tokenId, JudgeNodeHeartbeatRequest request) {
        return judgeNodeTokenMapper.update(null, new LambdaUpdateWrapper<JudgeNodeToken>()
                .eq(JudgeNodeToken::getTokenId, tokenId)
                .eq(JudgeNodeToken::getStatus, JudgeNodeConstant.TOKEN_ACTIVE)
                .set(JudgeNodeToken::getNodeName, request.getNodeName())
                .set(JudgeNodeToken::getMaxConcurrency, request.getMaxConcurrency())
                .set(JudgeNodeToken::getRunningTasks, request.getRunningTasks())
                .set(JudgeNodeToken::getCpuCore, request.getCpuCore())
                .set(JudgeNodeToken::getVersion, request.getVersion())
                .set(JudgeNodeToken::getLastHeartbeatTime, LocalDateTime.now()));
    }

    private void updateHeartbeat(Long id, JudgeNodeHeartbeatRequest request) {
        judgeNodeTokenMapper.update(null, new LambdaUpdateWrapper<JudgeNodeToken>()
                .eq(JudgeNodeToken::getId, id)
                .set(JudgeNodeToken::getNodeName, request.getNodeName())
                .set(JudgeNodeToken::getStatus, JudgeNodeConstant.TOKEN_ACTIVE)
                .set(JudgeNodeToken::getMaxConcurrency, request.getMaxConcurrency())
                .set(JudgeNodeToken::getRunningTasks, request.getRunningTasks())
                .set(JudgeNodeToken::getCpuCore, request.getCpuCore())
                .set(JudgeNodeToken::getVersion, request.getVersion())
                .set(JudgeNodeToken::getLastHeartbeatTime, LocalDateTime.now()));
    }

    private void fillHeartbeat(JudgeNodeToken token, JudgeNodeHeartbeatRequest request) {
        token.setMaxConcurrency(request.getMaxConcurrency());
        token.setRunningTasks(request.getRunningTasks());
        token.setCpuCore(request.getCpuCore());
        token.setVersion(request.getVersion());
        token.setLastHeartbeatTime(LocalDateTime.now());
    }

    private String parseBearerToken(String authorizationHeader) {
        String normalized = StrUtil.trimToNull(authorizationHeader);
        if (normalized == null) {
            return null;
        }
        if (normalized.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return StrUtil.trimToNull(normalized.substring(BEARER_PREFIX.length()));
        }
        return normalized;
    }

    private void validateRange(Integer value, String fieldName, int max) {
        if (value != null && (value < 0 || value > max)) {
            throw new BizException(ResultCode.BAD_REQUEST, fieldName + " 不合法");
        }
    }

    private void validateRange(Long value, String fieldName, int max) {
        if (value != null && (value < 0 || value > max)) {
            throw new BizException(ResultCode.BAD_REQUEST, fieldName + " 不合法");
        }
    }

    private String formalTokenId(String nodeId) {
        return "formal-" + SecureUtil.sha256(nodeId).substring(0, FORMAL_TOKEN_ID_HASH_LENGTH);
    }
}
