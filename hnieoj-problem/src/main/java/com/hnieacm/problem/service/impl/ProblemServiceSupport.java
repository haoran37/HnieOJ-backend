package com.hnieacm.problem.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.problem.entity.Problem;
import com.hnieacm.problem.entity.ProblemTag;
import com.hnieacm.problem.entity.Tag;
import com.hnieacm.problem.mapper.ProblemMapper;
import com.hnieacm.problem.mapper.ProblemTagMapper;
import com.hnieacm.problem.mapper.TagMapper;
import org.slf4j.Logger;
import org.springframework.dao.DuplicateKeyException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 题目服务实现的共享辅助工具
 */
public final class ProblemServiceSupport {

    /**
     * @MethodName ProblemServiceSupport
     *
     * @Description 题目服务支持
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    private ProblemServiceSupport() {
    }

    /**
     * @MethodName requireProblemCode
     * @Param problemCode
     * @Description 校验并规范化 problemCode，确保其非空且有效
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    public static String requireProblemCode(String problemCode) {
        String normalizedProblemCode = StrUtil.trim(problemCode);
        if (StrUtil.isBlank(normalizedProblemCode)) {
            throw new BizException(ResultCode.BAD_REQUEST, "problemCode不能为空");
        }
        return normalizedProblemCode;
    }

    /**
     * @MethodName queryProblemByCode
     * @Param problemMapper
     * @Param problemCode
     * @Description 按 problemCode 查找题目
     * @Return @return {@link Problem }
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    public static Problem queryProblemByCode(ProblemMapper problemMapper, String problemCode) {
        String normalizedProblemCode = requireProblemCode(problemCode);
        Problem problem = problemMapper.selectOne(
                new LambdaQueryWrapper<Problem>().eq(Problem::getProblemCode, normalizedProblemCode)
        );
        if (problem == null) {
            throw new BizException(ResultCode.PROBLEM_NOT_FOUND, "题目不存在");
        }
        return problem;
    }

    /**
     * @MethodName normalizeTagNames
     * @Param tags
     * @Description 规范化标签名称
     * @Return @return {@link List }<{@link String }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    public static List<String> normalizeTagNames(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }
        return tags.stream()
                .filter(StrUtil::isNotBlank)
                .flatMap(t -> Arrays.stream(t.split(",")))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .limit(50)
                .toList();
    }

    /**
     * @MethodName queryProblemPageWithTags
     * @Param problemMapper
     * @Param problemTagMapper
     * @Param tagMapper
     * @Param wrapper
     * @Param page
     * @Param pageSize
     * @Description 分页查询题目列表，并附带每个题目的标签信息
     * @Return @return {@link ProblemPageResult }
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    public static ProblemPageResult queryProblemPageWithTags(ProblemMapper problemMapper,
                                                             ProblemTagMapper problemTagMapper,
                                                             TagMapper tagMapper,
                                                             LambdaQueryWrapper<Problem> wrapper,
                                                             int page,
                                                             int pageSize) {
        wrapper.orderByDesc(Problem::getId);
        Page<Problem> mpPage = new Page<>(page, pageSize);
        Page<Problem> result = problemMapper.selectPage(mpPage, wrapper);

        List<Problem> records = result.getRecords() == null ? Collections.emptyList() : result.getRecords();
        List<Long> problemIds = records.stream().map(Problem::getId).filter(Objects::nonNull).toList();
        Map<Long, List<String>> tagsMap = queryTagsMap(problemTagMapper, tagMapper, problemIds);
        return new ProblemPageResult(records, result.getTotal(), tagsMap);
    }

    /**
     * @MethodName logUnpersistedLanguages
     * @Param logger
     * @Param problemCode
     * @Param languages
     * @Description 记录未被持久化语言
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    public static void logUnpersistedLanguages(Logger logger, String problemCode, List<String> languages) {
        if (logger == null || languages == null || languages.isEmpty()) {
            return;
        }
        logger.info("Problem languages received but not persisted, problemCode: {}, languages: {}",
                problemCode, languages);
    }

    /**
     * @MethodName queryTagsMap
     * @Param problemTagMapper
     * @Param tagMapper
     * @Param problemIds
     * @Description 查询标签映射
     * @Return @return {@link Map }<{@link Long }, {@link List }<{@link String }>>
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    public static Map<Long, List<String>> queryTagsMap(ProblemTagMapper problemTagMapper,
                                                        TagMapper tagMapper,
                                                        List<Long> problemIds) {
        if (problemIds == null || problemIds.isEmpty()) {
            return Map.of();
        }

        List<ProblemTag> rels = problemTagMapper.selectList(
                new LambdaQueryWrapper<ProblemTag>().in(ProblemTag::getProblemId, problemIds)
        );
        if (rels == null || rels.isEmpty()) {
            return Map.of();
        }

        Set<Long> tagIds = rels.stream()
                .map(ProblemTag::getTid)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (tagIds.isEmpty()) {
            return Map.of();
        }

        List<Tag> tags = tagMapper.selectBatchIds(tagIds);
        Map<Long, String> idToName = tags == null ? Map.of() : tags.stream()
                .filter(t -> t.getId() != null && StrUtil.isNotBlank(t.getName()))
                .collect(Collectors.toMap(Tag::getId, Tag::getName, (a, b) -> a));

        return rels.stream()
                .filter(r -> r.getProblemId() != null && r.getTid() != null)
                .collect(Collectors.groupingBy(
                        ProblemTag::getProblemId,
                        Collectors.mapping(r -> idToName.getOrDefault(r.getTid(), ""), Collectors.toList())
                ))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream().filter(StrUtil::isNotBlank).distinct().toList()
                ));
    }

    /**
     * @MethodName replaceProblemTags
     * @Param problemTagMapper
     * @Param tagMapper
     * @Param problemId
     * @Param tags
     * @Description 替换题目标签
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    public static void replaceProblemTags(ProblemTagMapper problemTagMapper,
                                          TagMapper tagMapper,
                                          Long problemId,
                                          List<String> tags) {
        if (problemId == null) {
            return;
        }

        List<String> names = normalizeTagNames(tags);
        problemTagMapper.delete(new LambdaQueryWrapper<ProblemTag>().eq(ProblemTag::getProblemId, problemId));
        if (names.isEmpty()) {
            return;
        }

        Map<String, Long> nameToId = ensureTags(tagMapper, names);
        if (nameToId.isEmpty()) {
            return;
        }

        List<ProblemTag> rels = nameToId.values().stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(tid -> {
                    ProblemTag rel = new ProblemTag();
                    rel.setProblemId(problemId);
                    rel.setTid(tid);
                    return rel;
                })
                .toList();

        for (ProblemTag rel : rels) {
            problemTagMapper.insert(rel);
        }
    }

    /**
     * @MethodName deleteProblemTagsByProblemId
     * @Param problemTagMapper
     * @Param problemId
     * @Description 按 problemId 删除题目标签
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    public static void deleteProblemTagsByProblemId(ProblemTagMapper problemTagMapper, Long problemId) {
        if (problemId == null) {
            return;
        }
        problemTagMapper.delete(new LambdaQueryWrapper<ProblemTag>().eq(ProblemTag::getProblemId, problemId));
    }

    /**
     * @MethodName ensureTags
     * @Param tagMapper
     * @Param names
     * @Description 确保标签存在
     * @Return @return {@link Map }<{@link String }, {@link Long }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    private static Map<String, Long> ensureTags(TagMapper tagMapper, List<String> names) {
        List<Tag> existed = tagMapper.selectList(new LambdaQueryWrapper<Tag>().in(Tag::getName, names));
        Map<String, Long> nameToId = existed == null ? new HashMap<>() : existed.stream()
                .filter(t -> StrUtil.isNotBlank(t.getName()) && t.getId() != null)
                .collect(Collectors.toMap(Tag::getName, Tag::getId, (a, b) -> a, HashMap::new));

        for (String name : names) {
            if (nameToId.containsKey(name)) {
                continue;
            }
            Tag t = new Tag();
            t.setName(name);
            try {
                tagMapper.insert(t);
            } catch (DuplicateKeyException ignored) {
                // 忽略并发场景中的重复写入
            }
        }

        List<Tag> all = tagMapper.selectList(new LambdaQueryWrapper<Tag>().in(Tag::getName, names));
        if (all != null) {
            for (Tag t : all) {
                if (StrUtil.isNotBlank(t.getName()) && t.getId() != null) {
                    nameToId.put(t.getName(), t.getId());
                }
            }
        }
        return nameToId;
    }

    /**
     * @Author: HaoRan_Lyu
     * @Date: 2026/02/21
     * @Description: 题目页面结果
     */
    public record ProblemPageResult(List<Problem> records, long total, Map<Long, List<String>> tagsMap) {

        public ProblemPageResult(List<Problem> records, long total, Map<Long, List<String>> tagsMap) {
            this.records = records == null ? Collections.emptyList() : records;
            this.total = total;
            this.tagsMap = tagsMap == null ? Map.of() : tagsMap;
        }

    }
}
