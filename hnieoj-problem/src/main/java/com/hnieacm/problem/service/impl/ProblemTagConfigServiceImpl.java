package com.hnieacm.problem.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.problem.dto.SaveTagConfigRequest;
import com.hnieacm.problem.dto.TagGroupRequest;
import com.hnieacm.problem.entity.ProblemTag;
import com.hnieacm.problem.entity.Tag;
import com.hnieacm.problem.mapper.ProblemTagMapper;
import com.hnieacm.problem.mapper.TagMapper;
import com.hnieacm.problem.service.ProblemTagConfigService;
import com.hnieacm.problem.vo.TagGroupVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 题目标签配置服务实现
 */
@Service
@RequiredArgsConstructor
public class ProblemTagConfigServiceImpl implements ProblemTagConfigService {

    private final TagMapper tagMapper;
    private final ProblemTagMapper problemTagMapper;

    @Override
    public List<TagGroupVo> list() {
        List<Tag> tags = tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                .orderByAsc(Tag::getCategory)
                .orderByAsc(Tag::getName));
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (Tag tag : tags) {
            String category = StrUtil.blankToDefault(tag.getCategory(), "未分类");
            grouped.computeIfAbsent(category, key -> new ArrayList<>()).add(tag.getName());
        }
        return grouped.entrySet().stream().map(entry -> {
            TagGroupVo vo = new TagGroupVo();
            vo.setTitle(entry.getKey());
            vo.setTags(entry.getValue());
            return vo;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(SaveTagConfigRequest request) {
        List<DesiredTag> desiredTags = normalize(request);
        List<Tag> existingTags = tagMapper.selectList(new LambdaQueryWrapper<Tag>());
        Map<String, Tag> existingByName = existingTags.stream()
                .collect(Collectors.toMap(Tag::getName, Function.identity(), (a, b) -> a));
        Set<String> desiredNames = desiredTags.stream().map(DesiredTag::name).collect(Collectors.toCollection(LinkedHashSet::new));

        deleteRemovedUnusedTags(existingTags, desiredNames);
        for (DesiredTag desiredTag : desiredTags) {
            Tag existing = existingByName.get(desiredTag.name());
            if (existing == null) {
                Tag tag = new Tag();
                tag.setName(desiredTag.name());
                tag.setCategory(desiredTag.category());
                tagMapper.insert(tag);
            } else if (!desiredTag.category().equals(existing.getCategory())) {
                tagMapper.update(null, new LambdaUpdateWrapper<Tag>()
                        .eq(Tag::getId, existing.getId())
                        .set(Tag::getCategory, desiredTag.category()));
            }
        }
    }

    private List<DesiredTag> normalize(SaveTagConfigRequest request) {
        if (request == null || request.getTags() == null || request.getTags().isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "tags 不能为空");
        }
        Set<String> names = new LinkedHashSet<>();
        List<DesiredTag> result = new ArrayList<>();
        for (TagGroupRequest group : request.getTags()) {
            String category = StrUtil.trimToNull(group == null ? null : group.getTitle());
            if (category == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "标签组标题不能为空");
            }
            if (group.getTags() == null || group.getTags().isEmpty()) {
                throw new BizException(ResultCode.BAD_REQUEST, "标签组不能为空");
            }
            for (String item : group.getTags()) {
                String name = StrUtil.trimToNull(item);
                if (name == null) {
                    throw new BizException(ResultCode.BAD_REQUEST, "标签名不能为空");
                }
                if (!names.add(name)) {
                    throw new BizException(ResultCode.BAD_REQUEST, "标签名重复：" + name);
                }
                result.add(new DesiredTag(category, name));
            }
        }
        result.sort(Comparator.comparing(DesiredTag::category).thenComparing(DesiredTag::name));
        return result;
    }

    private void deleteRemovedUnusedTags(List<Tag> existingTags, Set<String> desiredNames) {
        for (Tag tag : existingTags) {
            if (desiredNames.contains(tag.getName())) {
                continue;
            }
            Long usedCount = problemTagMapper.selectCount(new LambdaQueryWrapper<ProblemTag>()
                    .eq(ProblemTag::getTid, tag.getId()));
            if (usedCount != null && usedCount > 0) {
                throw new BizException(ResultCode.BAD_REQUEST, "标签已被题目使用，不能删除：" + tag.getName());
            }
            tagMapper.deleteById(tag.getId());
        }
    }

    private record DesiredTag(String category, String name) {
    }
}
