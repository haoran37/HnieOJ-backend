package com.hnieacm.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hnieacm.user.entity.UserInfo;
import com.hnieacm.user.vo.UserListVo;
import com.hnieacm.user.vo.PermissionUserVo;
import com.hnieacm.user.vo.UserSearchVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/14
 * @Description: 用户信息 Mapper
 */
@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfo> {

    /**
     * 权限用户列表
     */
    IPage<PermissionUserVo> selectPermissionUsers(IPage<PermissionUserVo> page, @Param("roleIds") List<Long> roleIds);

    /**
     * 搜索用户
     */
    IPage<UserSearchVo> searchUsers(IPage<UserSearchVo> page, @Param("query") String query);

    /**
     * 用户列表（复合筛选）
     */
    IPage<UserListVo> selectUserList(IPage<UserListVo> page,
                                    @Param("keyword") String keyword,
                                    @Param("collegeId") Long collegeId,
                                    @Param("grade") String grade,
                                    @Param("classId") Long classId);
}
