package com.hnieacm.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/14
 * @Description: 通用分页返回结构
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageVo<T> {

    private List<T> list;

    private long total;
}

