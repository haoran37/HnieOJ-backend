package com.hnieacm.judge.service;

import com.hnieacm.judge.dto.RemoteJudgeAccountCreateRequest;
import com.hnieacm.judge.dto.RemoteJudgeAccountUpdateRequest;
import com.hnieacm.judge.vo.RemoteJudgeAccountVo;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/03/01
 * @Description: 远程评测账号服务（仅账号 CRUD，不涉及外站代交执行）
 */
public interface RemoteJudgeAccountService {

    List<RemoteJudgeAccountVo> listRemoteJudgeAccounts(String oj, Integer status);

    /**
     * 创建账号：OJ+username 唯一，密码必填且只写不读
     */
    void createRemoteJudgeAccount(RemoteJudgeAccountCreateRequest request);

    /**
     * 更新账号：不存在返回 404；密码缺失或空白保留原密码，非空密码原样保存
     */
    void updateRemoteJudgeAccount(Integer id, RemoteJudgeAccountUpdateRequest request);

    /**
     * 删除账号：不存在返回 404
     */
    void deleteRemoteJudgeAccount(Integer id);
}
