package com.hnieacm.judge.service;

import com.hnieacm.judge.dto.CreateJudgeAuthCodeRequest;
import com.hnieacm.judge.dto.ExchangeJudgeTempTokenRequest;
import com.hnieacm.judge.dto.ValidateJudgeNodeTokenRequest;
import com.hnieacm.judge.vo.JudgeAuthCodeVo;
import com.hnieacm.judge.vo.JudgeNodeTokenValidationVo;
import com.hnieacm.judge.vo.JudgeNodeTokenVo;
import com.hnieacm.judge.vo.JudgeTempTokenVo;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 判题节点安全服务
 */
public interface JudgeNodeSecurityService {

    JudgeAuthCodeVo createAuthCode(CreateJudgeAuthCodeRequest request);

    JudgeTempTokenVo exchangeTempToken(ExchangeJudgeTempTokenRequest request);

    List<JudgeNodeTokenVo> listTokens(String status);

    void revokeToken(String tokenId);

    JudgeNodeTokenValidationVo validateToken(ValidateJudgeNodeTokenRequest request);
}
