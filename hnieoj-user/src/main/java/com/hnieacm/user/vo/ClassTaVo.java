package com.hnieacm.user.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/17
 * @Description: 班级助教信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassTaVo {

    /**
     * user_info.uid
     */
    private String uid;

    /**
     * 优先 realname，兜底 username，再兜底 uid。
     */
    private String name;
}
