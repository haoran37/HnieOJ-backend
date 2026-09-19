package com.hnieacm.judge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hnieacm.judge.entity.JudgeNodeToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 判题节点注册事实 Mapper。
 *
 * @author Codex
 */
@Mapper
public interface JudgeNodeTokenMapper extends BaseMapper<JudgeNodeToken> {

    /**
     * 对节点行加排他锁并读取当前会话纪元，用于在同事务内原子自增。
     *
     * <p>行锁保证并发接管串行化，每次新连接获得互不相同的纪元；
     * 只有 active/draining 节点可被接管。</p>
     *
     * @param nodeId 节点 registryID
     * @return 当前会话纪元；节点不可接管时返回 null
     */
    @Select("SELECT session_epoch FROM judge_node_token WHERE token_id = #{nodeId} "
            + "AND status IN ('active', 'draining') FOR UPDATE")
    Long lockSessionEpoch(@Param("nodeId") String nodeId);

    /**
     * 在行锁保护下原子自增会话纪元，使新连接独占新纪元并接管旧会话。
     *
     * @param nodeId 节点 registryID
     * @return 受影响行数
     */
    @Update("UPDATE judge_node_token SET session_epoch = session_epoch + 1, last_seen_at = NOW() "
            + "WHERE token_id = #{nodeId} AND status IN ('active', 'draining')")
    int incrementSessionEpoch(@Param("nodeId") String nodeId);

    /**
     * 对节点行加排他锁，用于轮换等需要串行化的关键操作。
     *
     * @param nodeId 节点 registryID
     * @return 节点注册事实；不存在时返回 null
     */
    @Select("SELECT * FROM judge_node_token WHERE token_id = #{nodeId} FOR UPDATE")
    JudgeNodeToken lockNode(@Param("nodeId") String nodeId);

    /**
     * 在指定会话纪元下刷新心跳时间；纪元不匹配时（旧连接）不更新。
     *
     * @param nodeId       节点 registryID
     * @param sessionEpoch 连接持有的会话纪元
     * @return 受影响行数，0 表示旧会话已被接管
     */
    @Update("UPDATE judge_node_token SET last_heartbeat_time = NOW(), last_seen_at = NOW() "
            + "WHERE token_id = #{nodeId} AND session_epoch = #{sessionEpoch} "
            + "AND status IN ('active', 'draining')")
    int touchSessionHeartbeat(@Param("nodeId") String nodeId, @Param("sessionEpoch") long sessionEpoch);
}
