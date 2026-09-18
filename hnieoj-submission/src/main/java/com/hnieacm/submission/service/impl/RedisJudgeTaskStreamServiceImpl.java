package com.hnieacm.submission.service.impl;

import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.properties.JudgeStreamProperties;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.submission.dto.JudgeTaskPendingScan;
import com.hnieacm.submission.dto.JudgeTaskStreamEntry;
import com.hnieacm.submission.service.JudgeTaskStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/18
 * @Description: 基于 Spring Data Redis 的判题任务 Streams 分发实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisJudgeTaskStreamServiceImpl implements JudgeTaskStreamService {

    private static final String FIELD_PAYLOAD = "payload";
    private static final String SPJ_JUDGE_MODE = "spj";
    private static final String INTERACTIVE_JUDGE_MODE = "interactive";

    /**
     * 原子 XACK + XDEL：先确认，只有确认成功（条目确属本消费组 PEL）才删除，避免误删仍在途的消息。
     * ACK 成功后崩溃不会留下“已确认但未删除”的记录，因为两步在同一 Lua 脚本内原子执行。
     */
    private static final DefaultRedisScript<Long> ACK_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            "local acked = redis.call('XACK', KEYS[1], ARGV[1], ARGV[2]) "
                    + "if acked == 1 then redis.call('XDEL', KEYS[1], ARGV[2]) end "
                    + "return acked",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final JudgeStreamProperties properties;

    @Override
    public String resolveStreamKey(String judgeMode) {
        if (SPJ_JUDGE_MODE.equalsIgnoreCase(judgeMode)) {
            return properties.getSpjStreamKey();
        }
        if (INTERACTIVE_JUDGE_MODE.equalsIgnoreCase(judgeMode)) {
            return properties.getInteractiveStreamKey();
        }
        return properties.getDefaultStreamKey();
    }

    @Override
    public String publish(String streamKey, String payloadJson) {
        if (streamKey == null || streamKey.isBlank()) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "判题任务 Stream key 不能为空");
        }
        StreamOperations<String, String, String> operations = streamOperations();
        ensureGroup(streamKey);
        // 不做 MAXLEN 裁剪：近似裁剪会连同仍被 PEL 引用的在途任务一起删除；在途任务只通过 ACK+XDEL 清理
        RecordId recordId = operations.add(MapRecord.create(streamKey, Map.of(FIELD_PAYLOAD, payloadJson)));
        if (recordId == null) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "判题任务写入 Redis Stream 失败");
        }
        return recordId.getValue();
    }

    @Override
    public List<JudgeTaskStreamEntry> readNew(String streamKey, String consumerName, int count) {
        ensureGroup(streamKey);
        StreamReadOptions options = StreamReadOptions.empty().count(Math.max(1, count));
        try {
            return toEntries(streamKey, readOnce(streamKey, consumerName, options));
        } catch (Exception e) {
            if (!isNoGroup(e)) {
                throw e;
            }
            // 消费组可能因 Redis 重建/清空而丢失，重建后重试一次
            ensureGroup(streamKey);
            return toEntries(streamKey, readOnce(streamKey, consumerName, options));
        }
    }

    private List<MapRecord<String, String, String>> readOnce(String streamKey, String consumerName,
                                                            StreamReadOptions options) {
        return streamOperations().read(
                Consumer.from(properties.getConsumerGroup(), consumerName),
                options,
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()));
    }

    @Override
    public JudgeTaskPendingScan scanPending(String streamKey, String consumerName, long minIdleMillis, int count,
                                            String afterId) {
        ensureGroup(streamKey);
        StreamOperations<String, String, String> operations = streamOperations();
        int batch = Math.max(1, count);
        Range<String> range;
        if (afterId == null || afterId.isBlank()) {
            range = Range.unbounded();
        } else {
            range = Range.rightUnbounded(Range.Bound.exclusive(afterId));
        }
        PendingMessages pending = operations.pending(streamKey, properties.getConsumerGroup(), range, batch);
        if (pending.isEmpty() && afterId != null && !afterId.isBlank()) {
            // 游标已越过 PEL 末尾时回卷到头部，保证每次调用都能推进而不是空转一轮
            pending = operations.pending(streamKey, properties.getConsumerGroup(), Range.unbounded(), batch);
        }
        if (pending.isEmpty()) {
            return new JudgeTaskPendingScan(List.of(), null, true);
        }
        List<RecordId> staleIds = new ArrayList<>();
        String lastId = null;
        for (PendingMessage message : pending) {
            lastId = message.getId().getValue();
            if (message.getElapsedTimeSinceLastDelivery().toMillis() >= minIdleMillis) {
                staleIds.add(message.getId());
            }
        }
        List<JudgeTaskStreamEntry> entries = staleIds.isEmpty() ? List.of()
                : toEntries(streamKey, operations.claim(
                        streamKey,
                        properties.getConsumerGroup(),
                        consumerName,
                        Duration.ofMillis(minIdleMillis),
                        staleIds.toArray(new RecordId[0])));
        boolean exhausted = pending.size() < batch;
        return new JudgeTaskPendingScan(entries, exhausted ? null : lastId, exhausted);
    }

    @Override
    public boolean ack(String streamKey, String recordId) {
        if (streamKey == null || recordId == null) {
            return false;
        }
        try {
            Long acked = stringRedisTemplate.execute(
                    ACK_AND_DELETE_SCRIPT, List.of(streamKey), properties.getConsumerGroup(), recordId);
            return acked != null && acked > 0;
        } catch (Exception e) {
            // Redis 不可用不能影响已提交的 MySQL 结果，交由恢复流程补偿
            log.warn("Judge task stream ack failed, streamKey: {}, recordId: {}", streamKey, recordId, e);
            return false;
        }
    }

    private StreamOperations<String, String, String> streamOperations() {
        return stringRedisTemplate.<String, String>opsForStream();
    }

    private void ensureGroup(String streamKey) {
        if (streamKey == null || streamKey.isBlank()) {
            return;
        }
        try {
            streamOperations().createGroup(streamKey, ReadOffset.from("0"), properties.getConsumerGroup());
        } catch (Exception e) {
            if (!containsInChain(e, "BUSYGROUP")) {
                log.debug("Ensure judge task stream group failed, streamKey: {}, msg: {}", streamKey, e.getMessage());
            }
        }
    }

    private boolean isNoGroup(Throwable e) {
        return containsInChain(e, "NOGROUP");
    }

    private boolean containsInChain(Throwable e, String token) {
        Throwable current = e;
        int depth = 0;
        while (current != null && depth++ < 10) {
            String message = current.getMessage();
            if (message != null && message.contains(token)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private List<JudgeTaskStreamEntry> toEntries(String streamKey, List<MapRecord<String, String, String>> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<JudgeTaskStreamEntry> entries = new ArrayList<>(records.size());
        for (MapRecord<String, String, String> record : records) {
            String payload = record.getValue() == null ? null : record.getValue().get(FIELD_PAYLOAD);
            entries.add(new JudgeTaskStreamEntry(streamKey, record.getId().getValue(), payload));
        }
        return entries;
    }
}
