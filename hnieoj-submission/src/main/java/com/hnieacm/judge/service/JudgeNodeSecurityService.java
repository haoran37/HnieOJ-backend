package com.hnieacm.judge.service;

import com.hnieacm.judge.dto.CreateFormalJudgeTokenRequest;
import com.hnieacm.judge.dto.CreateJudgeAuthCodeRequest;
import com.hnieacm.judge.dto.ExchangeJudgeTempTokenRequest;
import com.hnieacm.judge.dto.ValidateJudgeNodeTokenRequest;
import com.hnieacm.judge.vo.JudgeAuthCodeVo;
import com.hnieacm.judge.vo.JudgeNodeIdentity;
import com.hnieacm.judge.vo.JudgeNodeTokenValidationVo;
import com.hnieacm.judge.vo.JudgeNodeTokenVo;
import com.hnieacm.judge.vo.JudgeTempTokenVo;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 判题节点安全服务：临时/正式节点签发、稳定续期、撤销与身份校验
 */
public interface JudgeNodeSecurityService {

    JudgeAuthCodeVo createAuthCode(CreateJudgeAuthCodeRequest request);

    JudgeTempTokenVo exchangeTempToken(ExchangeJudgeTempTokenRequest request);

    JudgeTempTokenVo issueFormalToken(CreateFormalJudgeTokenRequest request);

    JudgeTempTokenVo renewToken(String authorizationHeader);

    List<JudgeNodeTokenVo> listTokens(String status);

    void revokeToken(String tokenId);

    void updateDraining(String tokenId, Boolean draining);

    /**
     * @MethodName resolveIdentity
     * @Param judgeToken 旧共享 X-Judge-Token，仅用于拒绝旧访问，不再参与鉴权
     * @Param authorizationHeader 逐节点 Bearer JWT
     * @Description 解析并校验运行期节点身份，失败抛 FORBIDDEN
     * @Return @return {@link JudgeNodeIdentity }
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    JudgeNodeIdentity resolveIdentity(String judgeToken, String authorizationHeader);

    /**
     * @MethodName validateToken
     * @Param request
     * @Description 兼容内部校验接口：不抛异常，返回 valid 布尔与身份
     * @Return @return {@link JudgeNodeTokenValidationVo }
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    JudgeNodeTokenValidationVo validateToken(ValidateJudgeNodeTokenRequest request);
}
