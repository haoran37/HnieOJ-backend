package com.hnieacm.achievement.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hnieacm.achievement.entity.AchievementApply;
import com.hnieacm.achievement.vo.AchievementApplyAdminVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 成就认证申请 Mapper
 */
@Mapper
public interface AchievementApplyMapper extends BaseMapper<AchievementApply> {

    /**
     * 管理员分页查询成就认证申请列表（带用户信息筛选）
     */
    IPage<AchievementApplyAdminVo> selectAdminApplyPage(Page<AchievementApplyAdminVo> page,
                                                        @Param("keyword") String keyword,
                                                        @Param("status") String status,
                                                        @Param("collegeId") Long collegeId);
}
