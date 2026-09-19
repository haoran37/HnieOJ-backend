package com.hnieacm.judge.service;

import com.hnieacm.common.judge.NodeAccessTokenService;
import com.hnieacm.judge.dto.CreateNodeBootstrapRequest;
import com.hnieacm.judge.dto.NodeAuthChallengeVo;
import com.hnieacm.judge.dto.NodeAuthResult;
import com.hnieacm.judge.dto.NodeEnrollRequest;
import com.hnieacm.judge.dto.NodeEnrollmentChallengeRequest;
import com.hnieacm.judge.dto.NodeRuntimeMetrics;
import com.hnieacm.judge.entity.JudgeNodeKey;
import com.hnieacm.judge.entity.JudgeNodeToken;
import com.hnieacm.judge.vo.NodeBootstrapVo;
import com.hnieacm.judge.vo.NodeEnrollVo;
import com.hnieacm.judge.vo.NodeEnrollmentChallengeVo;

/**
 * 节点身份协议 v1：Bootstrap、注册、WSS 认证与会话纪元校验。
 *
 * @author Codex
 */
public interface NodeIdentityService {

    /**
     * 管理员创建一次性 Bootstrap 凭据，明文只返回一次。
     *
     * @param request Bootstrap 策略请求
     * @return 一次性明文与过期时间
     */
    NodeBootstrapVo createBootstrap(CreateNodeBootstrapRequest request);

    /**
     * 申请注册挑战；已成功注册的同一身份可申请恢复挑战。
     *
     * @param request  注册身份声明
     * @param clientIp 来源 IP，仅用于限流
     * @return 一次性挑战
     */
    NodeEnrollmentChallengeVo createEnrollmentChallenge(NodeEnrollmentChallengeRequest request, String clientIp);

    /**
     * 提交注册证明，原子消费 Bootstrap 并创建节点与初始密钥。
     *
     * @param request 注册证明请求
     * @return 节点非机密身份
     */
    NodeEnrollVo enroll(NodeEnrollRequest request);

    /**
     * 生成绑定物理连接的认证挑战。
     *
     * @param connectionId 物理连接 ID
     * @param nodeId       刷新时的绑定节点 ID；首次连接为 null
     * @param sessionEpoch 刷新时连接持有的会话纪元；首次连接为 null
     * @param requestId    关联请求 ID
     * @param refresh      是否由 AUTH_REFRESH 发起
     * @return 挑战内容
     */
    NodeAuthChallengeVo createAuthChallenge(String connectionId, String nodeId, Long sessionEpoch,
                                            String requestId, boolean refresh);

    /**
     * 校验认证证明，签发短期令牌；refresh 时保持 sessionEpoch。
     *
     * @param connectionId 物理连接 ID
     * @param nodeId       节点 ID
     * @param keyId        密钥 ID
     * @param challengeId  挑战 ID
     * @param requestId    AUTH_RESPONSE 回传的请求 ID，必须与挑战绑定一致
     * @param signature    Ed25519 证明
     * @return 认证结果与配额快照
     */
    NodeAuthResult authenticate(String connectionId, String nodeId, String keyId, String challengeId,
                                String requestId, String signature);

    /**
     * 读取数据库权威会话纪元。
     *
     * @param nodeId 节点 ID
     * @return 当前会话纪元
     */
    long currentSessionEpoch(String nodeId);

    /**
     * 读取节点注册事实（不加锁、不抛业务异常），供有界连接巡检判断权威状态。
     *
     * @param nodeId 节点 ID
     * @return 节点注册事实；不存在时返回 null
     */
    JudgeNodeToken findNode(String nodeId);

    /**
     * 断言连接仍是节点的权威会话所有者，且节点/密钥/访问版本均有效。
     *
     * <p>关键业务操作前调用；旧连接被接管、节点禁用/吊销/到期、密钥过期或
     * accessVersion 变更都会被拒绝。</p>
     *
     * @param nodeId       节点 ID
     * @param keyId        连接绑定的密钥 ID
     * @param sessionEpoch 连接持有的会话纪元
     * @param accessVersion 连接令牌绑定的访问版本，可为 null 表示不校验
     * @param allowGrace   是否允许 GRACE 状态密钥
     * @return 当前节点注册事实
     */
    JudgeNodeToken requireSessionOwner(String nodeId, String keyId, long sessionEpoch,
                                       Integer accessVersion, boolean allowGrace);

    /**
     * 先对节点行加排他锁，再在同一事务内校验会话所有权与节点/密钥/版本状态。
     *
     * <p>数据平面关键写入必须使用该方法：持锁期间读到的状态是权威且稳定的，
     * 避免“先校验后写入”被并发的禁用/轮换/接管越过。调用方须处于事务中。</p>
     *
     * @param nodeId        节点 ID
     * @param keyId         密钥 ID
     * @param sessionEpoch  连接持有的会话纪元
     * @param accessVersion 连接令牌绑定的访问版本，可为 null 表示不校验
     * @param allowGrace    是否允许 GRACE 状态密钥
     * @return 加锁后的节点注册事实
     */
    JudgeNodeToken lockSessionOwner(String nodeId, String keyId, long sessionEpoch,
                                    Integer accessVersion, boolean allowGrace);

    /**
     * 在指定会话纪元下刷新心跳；旧会话不再具备业务写入权。
     *
     * @param nodeId       节点 ID
     * @param sessionEpoch 连接持有的纪元
     */
    void touchHeartbeat(String nodeId, long sessionEpoch);

    /**
     * 在指定会话纪元下刷新心跳并落库运行指标。
     *
     * <p>只更新运行指标列（cpu/version/runningTasks/cache/disk）与心跳时间，
     * 绝不覆盖 maxConcurrency、supportedJudgeModes、status、draining 等权威字段，
     * 因此 HEARTBEAT payload 无法提升配额/模式或解除排空。</p>
     *
     * @param nodeId       节点 ID
     * @param sessionEpoch 连接持有的纪元
     * @param metrics      运行指标，可为 null
     */
    void touchHeartbeat(String nodeId, long sessionEpoch, NodeRuntimeMetrics metrics);

    /**
     * 断言节点存在且处于可服务状态。
     *
     * @param nodeId 节点 ID
     * @return 节点注册事实
     */
    JudgeNodeToken requireServiceableNode(String nodeId);

    /**
     * 断言密钥存在、属于该节点且处于允许的状态。
     *
     * @param nodeId     节点 ID
     * @param keyId      密钥 ID
     * @param allowGrace 是否允许 GRACE 状态密钥
     * @return 密钥记录
     */
    JudgeNodeKey requireUsableKey(String nodeId, String keyId, boolean allowGrace);

    /**
     * 解析并校验短期 NODE_ACCESS 令牌。
     *
     * @param token 令牌明文
     * @return 可信声明
     */
    NodeAccessTokenService.ParsedToken parseAccessToken(String token);
}
