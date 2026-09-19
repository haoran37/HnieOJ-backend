package com.hnieacm.judge.service.impl;

import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.judge.service.FormalJudgeTokenService;
import com.hnieacm.judge.vo.JudgeFormalTokenVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 正式判题节点长期 Token 服务（共享 formalToken 已退休）。
 *
 * <p>共享 formalToken / Nacos 下发属于旧 bearer-only 备用通路，最终切换后不再提供任何可用凭据：
 * {@link #matches(String)} 恒为 false，初始化与轮换显式拒绝。正式与临时节点统一走
 * Bootstrap + Ed25519 注册与 {@code /ws/judge/node}。保留本实现仅为兼容既有 admin 端点词汇、
 * Bean 装配与数据库审计表。</p>
 */
@Slf4j
@Service
public class FormalJudgeTokenServiceImpl implements FormalJudgeTokenService {

    private static final String RETIRED_MESSAGE =
            "共享正式节点 Token 已退休，请使用 Bootstrap + Ed25519 重新入网";

    @Override
    public boolean matches(String rawToken) {
        // 共享 formalToken 通路已退休，任何令牌都不再被接受；调用方必须使用 NODE_ACCESS + Ed25519。
        log.warn("Retired shared formal judge token presented; rejecting and requiring Ed25519 node identity");
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JudgeFormalTokenVo rotate() {
        throw new BizException(ResultCode.FORBIDDEN, RETIRED_MESSAGE);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JudgeFormalTokenVo initializeIfNecessary() {
        throw new BizException(ResultCode.FORBIDDEN, RETIRED_MESSAGE);
    }
}
