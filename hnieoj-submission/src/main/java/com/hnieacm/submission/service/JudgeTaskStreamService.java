package com.hnieacm.submission.service;

import com.hnieacm.submission.dto.JudgeTaskPendingScan;
import com.hnieacm.submission.dto.JudgeTaskStreamEntry;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/18
 * @Description: 判题任务 Redis Streams 分发服务：XADD / XREADGROUP / XACK+XDEL / XCLAIM
 */
public interface JudgeTaskStreamService {

    /**
     * 按固定集合解析判题模式对应的 Stream key，不接受外部任意指定。
     *
     * @param judgeMode 判题模式（default/spj/interactive）
     * @return 固定的 Redis Stream key
     */
    String resolveStreamKey(String judgeMode);

    /**
     * XADD 一条判题任务消息。
     *
     * @param streamKey   已解析的固定 Stream key
     * @param payloadJson 判题任务消息 JSON
     * @return XADD 返回的消息 ID
     */
    String publish(String streamKey, String payloadJson);

    /**
     * XREADGROUP 非阻塞读取未投递过的新消息。
     *
     * @param streamKey    Stream key
     * @param consumerName 消费者名（节点 ID）
     * @param count        单次读取上限
     * @return 新消息条目
     */
    List<JudgeTaskStreamEntry> readNew(String streamKey, String consumerName, int count);

    /**
     * 从 afterId 之后有界扫描一批 PEL，仅认领空闲过久的条目并返回下一批游标，
     * 避免前面的活跃长任务永久饿死后面的孤儿。
     *
     * @param streamKey     Stream key
     * @param consumerName  认领消费者名
     * @param minIdleMillis 最小空闲毫秒数
     * @param count         单批扫描上限
     * @param afterId       上批游标；为空表示从 PEL 头部开始
     * @return 本批清理条目、下一批游标与是否已到 PEL 末尾
     */
    JudgeTaskPendingScan scanPending(String streamKey, String consumerName, long minIdleMillis, int count, String afterId);

    /**
     * 终态或跳过后原子 XACK + XDEL；失败只记日志，由恢复流程补偿。
     *
     * @param streamKey Stream key
     * @param recordId  消息 ID
     * @return 是否真正从 PEL 移除
     */
    boolean ack(String streamKey, String recordId);
}
