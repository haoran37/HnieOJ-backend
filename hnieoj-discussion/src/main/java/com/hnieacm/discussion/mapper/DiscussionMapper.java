package com.hnieacm.discussion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hnieacm.discussion.entity.Discussion;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;
import com.hnieacm.discussion.vo.ContributionRankVo;
import java.util.List;

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

    @Select("SELECT uid, SUM(points) AS contribution, "
            + "SUM(posts) AS posts, SUM(answers) AS answers FROM ("
            + "SELECT uid, 1 + 2 * COALESCE(like_num, 0) AS points, 1 AS posts, 0 AS answers "
            + "FROM discussion WHERE status = 0 UNION ALL "
            + "SELECT a.uid, 2 + COALESCE(a.like_num, 0) AS points, 0 AS posts, 1 AS answers "
            + "FROM discussion_answer a JOIN discussion d ON d.id = a.did "
            + "WHERE a.status = 0 AND d.status = 0) contributions "
            + "GROUP BY uid ORDER BY contribution DESC, posts DESC, uid ASC LIMIT 100")
    List<ContributionRankVo> listContributions();

    @Select("SELECT uid, SUM(points) AS contribution, "
            + "SUM(posts) AS posts, SUM(answers) AS answers FROM ("
            + "SELECT uid, 1 + 2 * COALESCE(like_num, 0) AS points, 1 AS posts, 0 AS answers "
            + "FROM discussion WHERE status = 0 UNION ALL "
            + "SELECT a.uid, 2 + COALESCE(a.like_num, 0) AS points, 0 AS posts, 1 AS answers "
            + "FROM discussion_answer a JOIN discussion d ON d.id = a.did "
            + "WHERE a.status = 0 AND d.status = 0) contributions WHERE uid = #{uid} GROUP BY uid")
    ContributionRankVo getContribution(@Param("uid") String uid);
}
