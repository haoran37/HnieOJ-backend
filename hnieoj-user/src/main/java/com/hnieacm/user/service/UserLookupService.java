package com.hnieacm.user.service;

import com.hnieacm.user.vo.UserBasicVo;
import com.hnieacm.user.vo.UserCheckVo;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/27
 * @Description: 用户查询服务
 */
public interface UserLookupService {

    UserCheckVo checkUser(String query);

    List<UserBasicVo> queryUserBasicInfoByUids(List<String> uids);
}
