package com.hnieacm.user.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/17
 * @Description: 通用下拉项（Long id + name）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdNameVo {

    private Long id;

    private String name;
}

