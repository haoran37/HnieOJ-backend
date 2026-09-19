package com.hnieacm.submission.dto;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/18
 * @Description: Redis Streams 中一条判题任务分发消息
 */
public record JudgeTaskStreamEntry(String streamKey, String recordId, String payload) {
}
