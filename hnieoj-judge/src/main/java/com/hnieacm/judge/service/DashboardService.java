package com.hnieacm.judge.service;

import com.hnieacm.judge.vo.DashboardContentVo;
import com.hnieacm.judge.vo.DashboardHealthVo;
import com.hnieacm.judge.vo.DashboardMetricsVo;
import com.hnieacm.judge.vo.ServiceStatusVo;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 管理端 dashboard 服务
 */
public interface DashboardService {

    DashboardMetricsVo metrics();

    DashboardHealthVo health();

    DashboardContentVo content();

    List<ServiceStatusVo> services();
}
