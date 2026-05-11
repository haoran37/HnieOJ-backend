package com.hnieacm.submission.config;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.submission.entity.Judge;
import com.hnieacm.submission.mapper.JudgeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/11
 * @Description: STOMP 连接与订阅鉴权
 */
@Component
@RequiredArgsConstructor
public class SubmissionWebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String SESSION_UID_KEY = "uid";
    private static final String SUBMISSION_PROGRESS_TOPIC_PREFIX = "/topic/submissions/";
    private static final String SUBMISSION_PROGRESS_TOPIC_SUFFIX = "/progress";

    private final JudgeMapper judgeMapper;

    /**
     * @MethodName preSend
     * @Param message
     * @Param channel
     * @Description 预发送
     * @Return @return {@link Message }<{@link ? }>
     * @Author HaoRan_Lyu
     * @Date 2026/05/11
     */
    @Override
    public Message<?> preSend(@Nonnull Message<?> message, @Nonnull MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            handleConnect(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            handleSubscribe(accessor);
        }
        return message;
    }

    /**
     * @MethodName handleConnect
     * @Param accessor
     * @Description 句柄连接
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/05/11
     */
    private void handleConnect(StompHeaderAccessor accessor) {
        String token = resolveToken(accessor.getFirstNativeHeader(AUTHORIZATION_HEADER));
        if (!StringUtils.hasText(token)) {
            throw new BizException(ResultCode.UNAUTHORIZED, "WebSocket 缺少 Authorization");
        }

        Object loginId;
        try {
            loginId = StpUtil.getLoginIdByToken(token);
        } catch (Exception e) {
            throw new BizException(ResultCode.UNAUTHORIZED, "WebSocket Token 无效");
        }
        if (loginId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "WebSocket Token 无效");
        }

        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "WebSocket 会话初始化失败");
        }
        sessionAttributes.put(SESSION_UID_KEY, String.valueOf(loginId));
    }

    /**
     * @MethodName handleSubscribe
     * @Param accessor
     * @Description 句柄订阅
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/05/11
     */
    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (!StringUtils.hasText(destination)) {
            throw new BizException(ResultCode.BAD_REQUEST, "订阅地址不能为空");
        }
        String submissionId = resolveSubmissionId(destination);
        if (!StringUtils.hasText(submissionId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不支持的订阅地址");
        }

        String uid = resolveSessionUid(accessor);
        Judge judge = judgeMapper.selectOne(new LambdaQueryWrapper<Judge>()
                .eq(Judge::getSubmitId, submissionId)
                .last("limit 1"));
        if (judge == null) {
            throw new BizException(ResultCode.SUBMISSION_NOT_FOUND, "提交记录不存在");
        }
        // 仅允许订阅自己的提交，管理员可通过 REST 查询全站记录。
        if (!uid.equals(judge.getUid())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权订阅该提交进度");
        }
    }

    /**
     * @MethodName resolveToken
     * @Param authorization
     * @Description 解析令牌
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/05/11
     */
    private String resolveToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        String value = authorization.trim();
        if (value.startsWith(BEARER_PREFIX)) {
            return value.substring(BEARER_PREFIX.length()).trim();
        }
        return value;
    }

    /**
     * @MethodName resolveSubmissionId
     * @Param destination
     * @Description 解析提交 id
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/05/11
     */
    private String resolveSubmissionId(String destination) {
        if (!destination.startsWith(SUBMISSION_PROGRESS_TOPIC_PREFIX)
                || !destination.endsWith(SUBMISSION_PROGRESS_TOPIC_SUFFIX)) {
            return null;
        }
        int start = SUBMISSION_PROGRESS_TOPIC_PREFIX.length();
        int end = destination.length() - SUBMISSION_PROGRESS_TOPIC_SUFFIX.length();
        if (start >= end) {
            return null;
        }
        return destination.substring(start, end);
    }

    /**
     * @MethodName resolveSessionUid
     * @Param accessor
     * @Description 解析会话 uid
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/05/11
     */
    private String resolveSessionUid(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null || sessionAttributes.get(SESSION_UID_KEY) == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "WebSocket 未登录");
        }
        return String.valueOf(sessionAttributes.get(SESSION_UID_KEY));
    }
}
