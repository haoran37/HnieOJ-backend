package com.hnieacm.problem.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.problem.dto.TagCreateRequest;
import com.hnieacm.problem.dto.TagUpdateRequest;
import com.hnieacm.problem.entity.ProblemTag;
import com.hnieacm.problem.entity.Tag;
import com.hnieacm.problem.mapper.ProblemTagMapper;
import com.hnieacm.problem.mapper.TagMapper;
import com.hnieacm.problem.service.TagService;
import com.hnieacm.problem.vo.TagVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 标签服务实现：目录读取 + 管理端增删改，删除被引用标签返回冲突。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_COLOR_LENGTH = 20;
    private static final int MAX_CATEGORY_LENGTH = 50;

    private final TagMapper tagMapper;
    private final ProblemTagMapper problemTagMapper;

    @Override
    public List<TagVo> listTags() {
        return tagMapper.selectList(new LambdaQueryWrapper<Tag>().orderByAsc(Tag::getId))
                .stream()
                .map(this::toVo)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createTag(TagCreateRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "标签参数不能为空");
        }
        String name = normalizeName(request.getName());
        String color = normalizeOptional(request.getColor(), MAX_COLOR_LENGTH, "color");
        String category = normalizeOptional(request.getCategory(), MAX_CATEGORY_LENGTH, "category");

        if (existsByName(name, null)) {
            throw new BizException(ResultCode.BAD_REQUEST, "标签名称已存在");
        }

        Tag tag = new Tag();
        tag.setName(name);
        tag.setColor(color);
        tag.setCategory(category);
        try {
            tagMapper.insert(tag);
        } catch (DuplicateKeyException e) {
            // 依赖 uk_name 兜底并发写入，冲突统一返回业务错误
            throw new BizException(ResultCode.BAD_REQUEST, "标签名称已存在");
        }
        log.info("Tag created, id: {}, name: {}", tag.getId(), name);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTag(Long id, TagUpdateRequest request) {
        if (id == null || id <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "id 不合法");
        }
        if (request == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "标签参数不能为空");
        }

        Tag existed = tagMapper.selectById(id);
        if (existed == null) {
            throw new BizException(ResultCode.NOT_FOUND, "标签不存在");
        }

        String name = normalizeName(request.getName());
        String color = normalizeOptional(request.getColor(), MAX_COLOR_LENGTH, "color");
        String category = normalizeOptional(request.getCategory(), MAX_CATEGORY_LENGTH, "category");

        if (existsByName(name, id)) {
            throw new BizException(ResultCode.BAD_REQUEST, "标签名称已存在");
        }

        try {
            // 显式 set 允许把 color/category 清空，而不是被 MyBatis-Plus 的 null 忽略策略跳过
            tagMapper.update(null, new LambdaUpdateWrapper<Tag>()
                    .eq(Tag::getId, id)
                    .set(Tag::getName, name)
                    .set(Tag::getColor, color)
                    .set(Tag::getCategory, category));
        } catch (DuplicateKeyException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "标签名称已存在");
        }
        log.info("Tag updated, id: {}, name: {}", id, name);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTag(Long id) {
        if (id == null || id <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "id 不合法");
        }

        // 事务内锁定 tag 行：与题目标签维护（replaceProblemTags 中同样使用 FOR UPDATE）串行化，
        // 避免删除与新建关联并发时产生悬挂的 problem_tag 引用。
        List<Tag> locked = tagMapper.selectList(
                new LambdaQueryWrapper<Tag>().eq(Tag::getId, id).last("FOR UPDATE")
        );
        if (locked == null || locked.isEmpty()) {
            throw new BizException(ResultCode.NOT_FOUND, "标签不存在");
        }

        Long referenceCount = problemTagMapper.selectCount(
                new LambdaQueryWrapper<ProblemTag>().eq(ProblemTag::getTid, id)
        );
        if (referenceCount != null && referenceCount > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "标签已被题目引用，请先解除关联");
        }

        tagMapper.deleteById(id);
        log.info("Tag deleted, id: {}", id);
    }

    private boolean existsByName(String name, Long excludeId) {
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<Tag>().eq(Tag::getName, name);
        if (excludeId != null) {
            wrapper.ne(Tag::getId, excludeId);
        }
        Long count = tagMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    private String normalizeName(String rawName) {
        String name = StrUtil.trim(rawName);
        if (StrUtil.isBlank(name)) {
            throw new BizException(ResultCode.BAD_REQUEST, "name 不能为空");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new BizException(ResultCode.BAD_REQUEST, "name 长度不能超过 " + MAX_NAME_LENGTH);
        }
        return name;
    }

    private String normalizeOptional(String rawValue, int maxLength, String field) {
        String value = StrUtil.trimToNull(rawValue);
        if (value != null && value.length() > maxLength) {
            throw new BizException(ResultCode.BAD_REQUEST, field + " 长度不能超过 " + maxLength);
        }
        return value;
    }

    private TagVo toVo(Tag tag) {
        TagVo vo = new TagVo();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        vo.setColor(tag.getColor());
        vo.setCategory(tag.getCategory());
        return vo;
    }
}
