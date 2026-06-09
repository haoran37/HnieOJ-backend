package com.hnieacm.contest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.common.util.PageParamUtils;
import com.hnieacm.contest.constant.ContestRuntimeStatusConstant;
import com.hnieacm.contest.entity.Contest;
import com.hnieacm.contest.entity.ContestProblem;
import com.hnieacm.contest.mapper.ContestMapper;
import com.hnieacm.contest.mapper.ContestProblemMapper;
import com.hnieacm.contest.vo.ContestProblemVo;
import org.slf4j.Logger;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/28
 * @Description: 比赛服务通用逻辑支撑
 */
public final class ContestServiceSupport {

    private ContestServiceSupport() {
    }

    /**
     * @MethodName validatePageParams
     * @Param page
     * @Param pageSize
     * @Description 校验分页参数
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static void validatePageParams(int page, int pageSize) {
        PageParamUtils.validate(page, pageSize);
    }

    /**
     * @MethodName selectContestPage
     * @Param contestMapper
     * @Param wrapper
     * @Param page
     * @Param pageSize
     * @Description 统一执行比赛分页查询
     * @Return @return {@link Page }<{@link Contest }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static Page<Contest> selectContestPage(ContestMapper contestMapper,
                                                  LambdaQueryWrapper<Contest> wrapper,
                                                  int page,
                                                  int pageSize) {
        Page<Contest> pageParam = new Page<>(page, pageSize);
        return contestMapper.selectPage(pageParam, wrapper);
    }

    /**
     * @MethodName queryContestPageData
     * @Param contestMapper
     * @Param contestProblemMapper
     * @Param wrapper
     * @Param page
     * @Param pageSize
     * @Description 查询比赛分页数据（含题目数量）
     * @Return @return {@link ContestPageData }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static ContestPageData queryContestPageData(ContestMapper contestMapper,
                                                       ContestProblemMapper contestProblemMapper,
                                                       LambdaQueryWrapper<Contest> wrapper,
                                                       int page,
                                                       int pageSize) {
        Page<Contest> pageResult = selectContestPage(contestMapper, wrapper, page, pageSize);
        List<Contest> records = getPageRecords(pageResult);
        Map<Long, Long> problemCountMap = queryProblemCountMap(contestProblemMapper,
                records.stream().map(Contest::getId).toList());
        return new ContestPageData(pageResult, records, problemCountMap);
    }

    /**
     * @MethodName buildContestPageVo
     * @Param contestMapper
     * @Param contestProblemMapper
     * @Param wrapper
     * @Param page
     * @Param pageSize
     * @Param listBuilder
     * @Description 构建比赛分页返回，统一处理空页逻辑
     * @Return @return {@link PageVo }<{@link T }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static <T> PageVo<T> buildContestPageVo(ContestMapper contestMapper,
                                                   ContestProblemMapper contestProblemMapper,
                                                   LambdaQueryWrapper<Contest> wrapper,
                                                   int page,
                                                   int pageSize,
                                                   Function<ContestPageData, List<T>> listBuilder) {
        ContestPageData pageData = queryContestPageData(contestMapper, contestProblemMapper, wrapper, page, pageSize);
        if (pageData.records().isEmpty()) {
            return emptyPageVo(pageData.pageResult());
        }
        List<T> list = listBuilder.apply(pageData);
        if (list == null || list.isEmpty()) {
            return emptyPageVo(pageData.pageResult());
        }
        return new PageVo<>(list, pageData.pageResult().getTotal());
    }

    /**
     * @MethodName getPageRecords
     * @Param pageResult
     * @Description 安全获取分页记录，避免空指针
     * @Return @return {@link List }<{@link Contest }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static List<Contest> getPageRecords(Page<Contest> pageResult) {
        if (pageResult == null || pageResult.getRecords() == null) {
            return Collections.emptyList();
        }
        return pageResult.getRecords();
    }

    /**
     * @MethodName emptyPageVo
     * @Param pageResult
     * @Description 构造空分页返回
     * @Return @return {@link PageVo }<{@link T }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static <T> PageVo<T> emptyPageVo(Page<?> pageResult) {
        long total = pageResult == null ? 0 : pageResult.getTotal();
        return new PageVo<>(Collections.emptyList(), total);
    }

    /**
     * 比赛分页数据载体
     */
    public record ContestPageData(Page<Contest> pageResult, List<Contest> records, Map<Long, Long> problemCountMap) {
    }

    /**
     * @MethodName getContestById
     * @Param contestMapper
     * @Param contestId
     * @Param notFoundMsg
     * @Description 通过 id 获取比赛
     * @Return @return {@link Contest }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static Contest getContestById(ContestMapper contestMapper, Long contestId, String notFoundMsg) {
        if (contestId == null || contestId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "contestId 不合法");
        }
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null) {
            throw new BizException(ResultCode.NOT_FOUND, notFoundMsg);
        }
        return contest;
    }

    /**
     * @MethodName queryProblemCountMap
     * @Param contestProblemMapper
     * @Param contestIds
     * @Description 查询比赛题目数量
     * @Return @return {@link Map }<{@link Long }, {@link Long }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static Map<Long, Long> queryProblemCountMap(ContestProblemMapper contestProblemMapper, List<Long> contestIds) {
        if (contestIds == null || contestIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> normalizedContestIds = contestIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (normalizedContestIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ContestProblem> relations = contestProblemMapper.selectList(new LambdaQueryWrapper<ContestProblem>()
                .select(ContestProblem::getCid)
                .in(ContestProblem::getCid, normalizedContestIds));
        if (relations == null || relations.isEmpty()) {
            return Collections.emptyMap();
        }
        return relations.stream().collect(Collectors.groupingBy(ContestProblem::getCid, Collectors.counting()));
    }

    /**
     * @MethodName toProblemVo
     * @Param problem
     * @Description 比赛题目 VO 映射
     * @Return @return {@link ContestProblemVo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static ContestProblemVo toProblemVo(ContestProblem problem) {
        ContestProblemVo vo = new ContestProblemVo();
        vo.setId(problem.getId());
        vo.setProblemId(problem.getProblemId());
        vo.setDisplayId(problem.getDisplayId());
        vo.setDisplayTitle(problem.getDisplayTitle());
        vo.setColor(problem.getColor());
        return vo;
    }

    /**
     * @MethodName parseCustomTags
     * @Param objectMapper
     * @Param log
     * @Param json
     * @Description 解析自定义标签
     * @Return @return {@link List }<{@link String }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static List<String> parseCustomTags(ObjectMapper objectMapper, Logger log, String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<String> tags = objectMapper.readValue(json, new TypeReference<>() {
            });
            if (tags == null || tags.isEmpty()) {
                return Collections.emptyList();
            }
            return tags.stream()
                    .map(tag -> tag == null ? null : tag.trim())
                    .filter(tag -> tag != null && !tag.isEmpty())
                    .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), List::copyOf));
        } catch (Exception e) {
            log.warn("Parse contest custom tags failed, tags json: {}", json, e);
            return Collections.emptyList();
        }
    }

    /**
     * @MethodName resolveRuntimeStatus
     * @Param startTime
     * @Param endTime
     * @Description 解析运行时状态
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static String resolveRuntimeStatus(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime now = LocalDateTime.now();
        if (startTime != null && now.isBefore(startTime)) {
            return ContestRuntimeStatusConstant.UPCOMING;
        }
        if (endTime != null && now.isAfter(endTime)) {
            return ContestRuntimeStatusConstant.ENDED;
        }
        return ContestRuntimeStatusConstant.RUNNING;
    }

    /**
     * @MethodName toLocalDateTime
     * @Param epochMillis
     * @Description 时间戳转 LocalDateTime
     * @Return @return {@link LocalDateTime }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static LocalDateTime toLocalDateTime(Long epochMillis) {
        if (epochMillis == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    /**
     * @MethodName toEpochMilli
     * @Param dateTime
     * @Description LocalDateTime 转毫秒时间戳
     * @Return @return long
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
