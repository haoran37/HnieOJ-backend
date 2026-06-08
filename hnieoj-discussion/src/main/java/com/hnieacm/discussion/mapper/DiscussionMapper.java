package com.hnieacm.discussion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hnieacm.discussion.entity.Discussion;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 讨论帖子 Mapper
 */
public interface DiscussionMapper extends BaseMapper<Discussion> {

    @Update("update discussion set view_num = coalesce(view_num, 0) + 1 where id = #{id} and status = 0")
    int incrementViewNum(@Param("id") Long id);

    @Update("update discussion set like_num = greatest(coalesce(like_num, 0) + #{delta}, 0) where id = #{id} and status = 0")
    int incrementLikeNum(@Param("id") Long id, @Param("delta") int delta);
}
