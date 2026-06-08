package com.hnieacm.discussion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hnieacm.discussion.entity.DiscussionAnswer;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 讨论回答 Mapper
 */
public interface DiscussionAnswerMapper extends BaseMapper<DiscussionAnswer> {

    @Update("update discussion_answer set like_num = greatest(coalesce(like_num, 0) + #{delta}, 0) where id = #{id} and status = 0")
    int incrementLikeNum(@Param("id") Long id, @Param("delta") int delta);
}
