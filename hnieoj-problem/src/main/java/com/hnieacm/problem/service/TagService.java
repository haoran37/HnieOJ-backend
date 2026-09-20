package com.hnieacm.problem.service;

import com.hnieacm.problem.dto.TagCreateRequest;
import com.hnieacm.problem.dto.TagUpdateRequest;
import com.hnieacm.problem.vo.TagVo;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 标签目录与标签管理服务
 */
public interface TagService {

    /**
     * 标签目录（登录可读）
     */
    List<TagVo> listTags();

    /**
     * 创建标签；名称去空格后校验长度与重名
     */
    void createTag(TagCreateRequest request);

    /**
     * 更新标签；不存在返回 NOT_FOUND，名称冲突返回业务错误
     */
    void updateTag(Long id, TagUpdateRequest request);

    /**
     * 删除标签；被题目引用时返回冲突，不删除关联关系
     */
    void deleteTag(Long id);
}
