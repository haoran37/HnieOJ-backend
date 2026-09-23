package com.hnieacm.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hnieacm.user.entity.UserFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import com.hnieacm.user.vo.FavoriteTrainingStatVo;
import java.util.List;

/**
 * @author HnieOJ contributors
 */
@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {
    /**
     * Return the five most-favorited training lists.
     *
     * @return training lists and favorite counts
     */
    @Select("SELECT target_id AS trainingId, COUNT(*) AS favorites FROM user_favorite "
            + "WHERE target_type = 'training' GROUP BY target_id ORDER BY favorites DESC, target_id LIMIT 5")
    List<FavoriteTrainingStatVo> topTrainings();
}
