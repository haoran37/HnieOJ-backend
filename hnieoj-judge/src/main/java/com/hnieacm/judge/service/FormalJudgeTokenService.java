package com.hnieacm.judge.service;

import com.hnieacm.judge.vo.JudgeFormalTokenVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 正式判题节点长期 Token 服务
 */
public interface FormalJudgeTokenService {

    boolean matches(String rawToken);

    JudgeFormalTokenVo rotate();
}
