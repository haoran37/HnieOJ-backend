package com.hnieacm.judge.service;

import com.hnieacm.judge.dto.NodeKeyRotationPrepareRequest;
import com.hnieacm.judge.vo.NodeKeyRotationVo;

/**
 * 节点密钥轮换：prepare → confirm 两阶段，幂等且可从崩溃中恢复。
 *
 * @author Codex
 */
public interface NodeKeyRotationService {

    /**
     * 两阶段轮换第一阶段：登记 pending 新密钥。
     *
     * @param caller  已完成签名的调用方（必须持有当前 ACTIVE 密钥）
     * @param request 轮换请求（rotationId、新公钥、新密钥证明）
     * @return 轮换公开状态
     */
    NodeKeyRotationVo prepare(SignedCaller caller, NodeKeyRotationPrepareRequest request);

    /**
     * 两阶段轮换第二阶段：激活新密钥并把旧密钥置为 grace。
     *
     * @param caller     已完成签名的调用方
     * @param rotationId 轮换 ID
     * @param signature  新密钥对 confirm 域的签名
     * @return 轮换公开状态
     */
    NodeKeyRotationVo confirm(SignedCaller caller, String rotationId, String signature);

    /**
     * 查询轮换公开状态，用于响应丢失恢复。
     *
     * @param caller     已完成签名的调用方
     * @param rotationId 轮换 ID
     * @return 轮换公开状态
     */
    NodeKeyRotationVo query(SignedCaller caller, String rotationId);
}
