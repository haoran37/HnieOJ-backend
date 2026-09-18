package com.hnieacm.judge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hnieacm.judge.entity.JudgeNodeToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 判题节点 Token Mapper
 */
@Mapper
public interface JudgeNodeTokenMapper extends BaseMapper<JudgeNodeToken> {

    /**
     * @MethodName selectByTokenIdForUpdate
     * @Param tokenId
     * @Description 加行锁读取节点 Token，用于下发额度与撤销竞态
     * @Return @return {@link JudgeNodeToken }
     * @Author HaoRan_Lyu
     * @Date 2026/09/18
     */
    @Select("SELECT * FROM judge_node_token WHERE token_id = #{tokenId} LIMIT 1 FOR UPDATE")
    JudgeNodeToken selectByTokenIdForUpdate(@Param("tokenId") String tokenId);
}
