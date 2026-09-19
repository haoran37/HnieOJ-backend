package com.hnieacm.judge.service;

import com.hnieacm.judge.dto.UpdateNodePolicyRequest;
import com.hnieacm.judge.vo.JudgeNodeTokenVo;

/**
 * 判题节点生命周期管理（管理员操作）。
 *
 * <p>所有状态迁移在节点行锁内串行执行，保证与认证/轮换/数据平面校验的锁顺序一致。
 * drain 只停止新任务分发、不提升 accessVersion，因此在途租约/结果续报不受影响；
 * disable/enable/policy 属于权限变更，提升 accessVersion 使旧短期授权失效。
 * 连接关闭与 NODE_STATE/TASK_CANCEL 通知由各实例的有界 DB 巡检完成，
 * 不依赖本机事件，也不新增 pubsub 框架。</p>
 *
 * @author Codex
 */
public interface JudgeNodeLifecycleService {

    /**
     * 将节点置为排空：停止新任务分发，保留心跳/续租/结果回传。
     *
     * @param nodeId 节点 registryID
     * @return 更新后的节点展示 Vo（不序列化 DB 凭证实体）
     */
    JudgeNodeTokenVo drain(String nodeId);

    /**
     * 禁用节点：提升 accessVersion，推 NODE_STATE/TASK_CANCEL 并关闭连接。
     *
     * @param nodeId 节点 registryID
     * @return 更新后的节点展示 Vo（不序列化 DB 凭证实体）
     */
    JudgeNodeTokenVo disable(String nodeId);

    /**
     * 重新启用节点；被吊销或已硬到期的节点不可复活。
     *
     * @param nodeId 节点 registryID
     * @return 更新后的节点展示 Vo（不序列化 DB 凭证实体）
     */
    JudgeNodeTokenVo enable(String nodeId);

    /**
     * 更新节点运行策略；权限相关字段变更提升 accessVersion。
     *
     * @param nodeId  节点 registryID
     * @param request 策略更新请求
     * @return 更新后的节点展示 Vo（不序列化 DB 凭证实体）
     */
    JudgeNodeTokenVo updatePolicy(String nodeId, UpdateNodePolicyRequest request);
}
