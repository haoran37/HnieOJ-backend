package com.hnieacm.judge.service;

import com.hnieacm.judge.dto.SystemConfigSaveRequest;
import com.hnieacm.judge.vo.SystemConfigVo;
import com.hnieacm.judge.vo.SystemPublicConfigVo;
import com.hnieacm.judge.vo.SystemTimeVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/03/01
 * @Description: 系统配置服务
 */
public interface SystemConfigService {

    SystemPublicConfigVo getPublicConfig();

    SystemTimeVo getSystemTime();

    SystemConfigVo getSystemConfig();

    void saveSystemConfig(SystemConfigSaveRequest request);
}
