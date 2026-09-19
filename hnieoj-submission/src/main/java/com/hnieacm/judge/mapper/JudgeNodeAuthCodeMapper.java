package com.hnieacm.judge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hnieacm.judge.entity.JudgeNodeAuthCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 判题节点 Bootstrap 凭据 Mapper。
 *
 * @author Codex
 */
@Mapper
public interface JudgeNodeAuthCodeMapper extends BaseMapper<JudgeNodeAuthCode> {

    /**
     * 原子消费 Bootstrap：仅当仍启用、未过期且兑换次数未达上限时成功。
     *
     * @param id            Bootstrap 记录 ID
     * @param enrollmentId  本次注册的 enrollment ID
     * @param publicKeyHash 注册公钥 SHA-256 摘要
     * @param nodeId        生成的节点 ID
     * @return 受影响行数，0 表示并发争用失败或已过期
     */
    @Update("UPDATE judge_node_auth_code SET used_count = used_count + 1, status = 'consumed', "
            + "enrollment_id = #{enrollmentId}, public_key_hash = #{publicKeyHash}, "
            + "node_id = #{nodeId}, consumed_time = NOW() "
            + "WHERE id = #{id} AND status = 'enabled' AND used_count < max_exchange_count "
            + "AND expire_time > NOW()")
    int consumeBootstrap(@Param("id") Long id,
                         @Param("enrollmentId") String enrollmentId,
                         @Param("publicKeyHash") String publicKeyHash,
                         @Param("nodeId") String nodeId);
}
