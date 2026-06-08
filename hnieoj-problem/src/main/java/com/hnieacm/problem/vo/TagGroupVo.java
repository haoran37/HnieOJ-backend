package com.hnieacm.problem.vo;

import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 标签分组视图对象
 */
@Data
public class TagGroupVo {

    private String title;

    private List<String> tags;
}
