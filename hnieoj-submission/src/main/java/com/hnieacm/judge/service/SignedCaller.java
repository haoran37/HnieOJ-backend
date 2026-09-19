package com.hnieacm.judge.service;

/**
 * 已完成签名的 HTTP 调用方身份。
 *
 * @param nodeId        节点 ID
 * @param keyId         绑定密钥 ID
 * @param accessVersion 令牌绑定的访问版本，用于与数据库权威值比较
 * @param sessionEpoch  令牌绑定的会话纪元，用于拒绝被接管会话
 * @author Codex
 */
public record SignedCaller(String nodeId, String keyId, Integer accessVersion, long sessionEpoch) {
}
