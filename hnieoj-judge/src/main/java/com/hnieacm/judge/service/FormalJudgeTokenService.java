package com.hnieacm.judge.service;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 正式判题节点长期 Token 校验服务
 */
public interface FormalJudgeTokenService {

    boolean matches(String rawToken);
}
