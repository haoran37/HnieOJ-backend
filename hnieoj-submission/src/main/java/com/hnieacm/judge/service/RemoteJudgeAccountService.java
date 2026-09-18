package com.hnieacm.judge.service;

import com.hnieacm.judge.vo.RemoteJudgeAccountVo;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/03/01
 * @Description: 远程评测账号服务
 */
public interface RemoteJudgeAccountService {

    List<RemoteJudgeAccountVo> listRemoteJudgeAccounts(String oj, Integer status);
}
