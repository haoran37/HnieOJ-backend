package com.hnieacm.common.judge;

/**
 * 节点身份协议 v1 常量。
 *
 * <p>协议见 docs/protocol-v1.md（冻结输入）。字段、路径、签名字节顺序均在此集中定义，
 * 供 submission / problem / gateway 复用，避免各模块散落硬编码。</p>
 *
 * @author Codex
 */
public final class NodeProtocolConstants {

    private NodeProtocolConstants() {
    }

    /** 协议版本号，WebSocket 信封与 REST 均由该值标识。 */
    public static final int PROTOCOL_VERSION = 1;

    /** 签名域分隔常量：注册。 */
    public static final String DOMAIN_ENROLL = "HNIEOJ-ENROLL-V1";
    public static final String DOMAIN_AUTH = "HNIEOJ-AUTH-V1";
    public static final String DOMAIN_HTTP = "HNIEOJ-HTTP-V1";
    public static final String DOMAIN_ROTATE_PREPARE = "HNIEOJ-ROTATE-PREPARE-V1";
    public static final String DOMAIN_ROTATE_CONFIRM = "HNIEOJ-ROTATE-CONFIRM-V1";

    /** 节点角色：正式节点。 */
    public static final String NODE_TYPE_FORMAL = "formal";
    public static final String NODE_TYPE_TEMP = "temp";

    /** 节点状态：可服务。 */
    public static final String NODE_STATUS_ACTIVE = "active";
    public static final String NODE_STATUS_DISABLED = "disabled";
    public static final String NODE_STATUS_DRAINING = "draining";
    public static final String NODE_STATUS_REVOKED = "revoked";
    public static final String NODE_STATUS_EXPIRED = "expired";

    /** 密钥状态：待确认。 */
    public static final String KEY_STATUS_PENDING = "PENDING";
    public static final String KEY_STATUS_ACTIVE = "ACTIVE";
    public static final String KEY_STATUS_GRACE = "GRACE";
    public static final String KEY_STATUS_REVOKED = "REVOKED";
    public static final String KEY_STATUS_EXPIRED = "EXPIRED";

    /** Bootstrap 状态：启用。 */
    public static final String BOOTSTRAP_STATUS_ENABLED = "enabled";
    public static final String BOOTSTRAP_STATUS_CONSUMED = "consumed";
    public static final String BOOTSTRAP_STATUS_REVOKED = "revoked";
    public static final String BOOTSTRAP_STATUS_EXPIRED = "expired";

    /** 令牌类型：节点短期访问令牌。 */
    public static final String TOKEN_TYPE_NODE_ACCESS = "NODE_ACCESS";
    public static final String TOKEN_SCOPE_NODE_TASK = "node:task";

    /** WebSocket 消息类型：服务端认证挑战。 */
    public static final String WS_AUTH_CHALLENGE = "AUTH_CHALLENGE";
    public static final String WS_AUTH_RESPONSE = "AUTH_RESPONSE";
    public static final String WS_AUTH_REFRESH = "AUTH_REFRESH";
    public static final String WS_AUTH_OK = "AUTH_OK";
    public static final String WS_RESUME_TASKS = "RESUME_TASKS";
    public static final String WS_RESUME_RESULT = "RESUME_RESULT";
    public static final String WS_READY = "READY";
    public static final String WS_TASK_ASSIGN = "TASK_ASSIGN";
    public static final String WS_TASK_ACK = "TASK_ACK";
    public static final String WS_TASK_RUNNING = "TASK_RUNNING";
    public static final String WS_TASK_EVENT_ACK = "TASK_EVENT_ACK";
    public static final String WS_TASK_RESULT = "TASK_RESULT";
    public static final String WS_TASK_RESULT_ACK = "TASK_RESULT_ACK";
    public static final String WS_LEASE_RENEW = "LEASE_RENEW";
    public static final String WS_LEASE_RENEWED = "LEASE_RENEWED";
    public static final String WS_TASK_CANCEL = "TASK_CANCEL";
    public static final String WS_HEARTBEAT = "HEARTBEAT";
    public static final String WS_HEARTBEAT_ACK = "HEARTBEAT_ACK";
    public static final String WS_NODE_DRAIN = "NODE_DRAIN";
    public static final String WS_NODE_STATE = "NODE_STATE";
    public static final String WS_ERROR = "ERROR";
    public static final String WS_PING = "PING";
    public static final String WS_PONG = "PONG";

    /** 节点 WebSocket 端点路径。 */
    public static final String WS_PATH_JUDGE_NODE = "/ws/judge/node";
    public static final String WS_PATH_ATTR_NODE_ID = "nodeId";
    public static final String WS_PATH_ATTR_KEY_ID = "keyId";

    /** 节点协议 REST 路径：注册。 */
    public static final String REST_ENROLLMENT_CHALLENGES = "/judge/nodes/enrollment-challenges";
    public static final String REST_ENROLL = "/judge/nodes/enroll";
    public static final String REST_ROTATIONS = "/judge/nodes/keys/rotations";
    public static final String REST_ROTATION_BY_ID = "/judge/nodes/keys/rotations/{rotationId}";

    /** HTTP 签名头：节点 ID。 */
    public static final String HEADER_NODE_ID = "X-Judge-Node-Id";
    public static final String HEADER_KEY_ID = "X-Judge-Key-Id";
    public static final String HEADER_TIMESTAMP = "X-Judge-Timestamp";
    public static final String HEADER_NONCE = "X-Judge-Nonce";
    public static final String HEADER_SIGNATURE = "X-Judge-Signature";

    /** 错误码：未认证。 */
    public static final String ERROR_UNAUTHORIZED = "UNAUTHORIZED";
    public static final String ERROR_EPOCH_STALE = "SESSION_EPOCH_STALE";
    public static final String ERROR_IDENTITY_INACTIVE = "IDENTITY_INACTIVE";
    public static final String ERROR_BAD_REQUEST = "BAD_REQUEST";
    public static final String ERROR_REPLAY = "NONCE_REPLAY";
    public static final String ERROR_INTERNAL = "INTERNAL_ERROR";

    /** Redis 键前缀：注册挑战。 */
    public static final String REDIS_ENROLL_CHALLENGE_PREFIX = "hnieoj:judge:node:enroll:challenge:";
    public static final String REDIS_AUTH_CHALLENGE_PREFIX = "hnieoj:judge:node:auth:challenge:";
    public static final String REDIS_HTTP_NONCE_PREFIX = "hnieoj:judge:node:http:nonce:";
    public static final String REDIS_NODE_SESSION_PREFIX = "hnieoj:judge:node:session:";
    public static final String REDIS_BOOTSTRAP_RATE_PREFIX = "hnieoj:judge:node:bootstrap:rate:";
}
