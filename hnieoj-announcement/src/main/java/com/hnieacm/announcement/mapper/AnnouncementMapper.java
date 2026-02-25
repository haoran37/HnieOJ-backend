package com.hnieacm.announcement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hnieacm.announcement.entity.Announcement;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/24
 * @Description: 公告 Mapper
 */
@Mapper
public interface AnnouncementMapper extends BaseMapper<Announcement> {
}
