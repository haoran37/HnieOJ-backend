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
     * @MethodName resolveStreamKey
     * @Param judgeMode
     * @Description 按固定集合解析 Stream key，不接受外部指定
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    String resolveStreamKey(String judgeMode);

    /**
     * @MethodName publish
     * @Param streamKey
     * @Param payloadJson
     * @Description XADD 一条判题任务，返回消息 ID
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    String publish(String streamKey, String payloadJson);

    /**
     * @MethodName readNew
     * @Param streamKey
     * @Param consumerName
     * @Param count
     * @Description XREADGROUP 读取未投递过的新消息，非阻塞
     * @Return @return {@link List }<{@link JudgeTaskStreamEntry }>
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    List<JudgeTaskStreamEntry> readNew(String streamKey, String consumerName, int count);

    /**
     * @MethodName scanPending
     * @Param streamKey
     * @Param consumerName
     * @Param minIdleMillis
     * @Param count
     * @Param afterId
     * @Description 从 afterId 之后有界扫描一批 PEL：仅认领空闲过久的条目，并返回下一批游标，
     * 避免前面的活跃长任务永久饿死后面的孤儿。afterId 为空表示从 PEL 头部开始。
     * @Return @return {@link JudgeTaskPendingScan }
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    JudgeTaskPendingScan scanPending(String streamKey, String consumerName, long minIdleMillis, int count, String afterId);

    /**
     * @MethodName ack
     * @Param streamKey
     * @Param recordId
     * @Description 终态或跳过后原子 XACK + XDEL，返回是否真正从 PEL 移除；失败只记日志
     * @Return @return boolean
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    boolean ack(String streamKey, String recordId);
}
