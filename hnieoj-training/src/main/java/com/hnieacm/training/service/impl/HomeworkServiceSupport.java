package com.hnieacm.training.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.training.constant.HomeworkStatusConstant;
import com.hnieacm.training.entity.Homework;
import com.hnieacm.training.entity.HomeworkClass;
import com.hnieacm.training.entity.HomeworkProblem;
import com.hnieacm.training.mapper.HomeworkClassMapper;
import com.hnieacm.training.mapper.HomeworkMapper;
import com.hnieacm.training.mapper.HomeworkProblemMapper;
import com.hnieacm.training.vo.HomeworkProblemVo;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/28
 * @Description: 作业服务通用支撑方法
 */
public final class HomeworkServiceSupport {

    private static final int DISPLAY_ID_RADIX = 26;
    private static final int DISPLAY_ID_MIN_VALUE = 1;
    private static final char DISPLAY_ID_FIRST_CHAR = 'A';
    private static final Pattern DISPLAY_ID_PATTERN = Pattern.compile("^[A-Z]+$");

    private HomeworkServiceSupport() {
    }

    /**
     * @MethodName validatePageParams
     * @Param page
     * @Param pageSize
     * @Description 验证分页参数
     * @Return
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static void validatePageParams(int page, int pageSize) {
        if (page <= 0 || pageSize <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "page 和 pageSize 必须大于 0");
        }
    }

    /**
     * @MethodName trimToNull
     * @Param value
     * @Description 去除首尾空格，若为空返回 null
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * @MethodName queryHomeworkPageData
     * @Param homeworkMapper
     * @Param homeworkProblemMapper
     * @Param homeworkClassMapper
     * @Param wrapper
     * @Param page
     * @Param pageSize
     * @Description 查询作业分页数据与关联数量
     * @Return @return {@link HomeworkPageData }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static HomeworkPageData queryHomeworkPageData(HomeworkMapper homeworkMapper,
                                                         HomeworkProblemMapper homeworkProblemMapper,
                                                         HomeworkClassMapper homeworkClassMapper,
                                                         LambdaQueryWrapper<Homework> wrapper,
                                                         int page,
                                                         int pageSize) {
        validatePageParams(page, pageSize);

        Page<Homework> pageParam = new Page<>(page, pageSize);
        Page<Homework> pageResult = homeworkMapper.selectPage(pageParam, wrapper);
        List<Homework> records = pageResult.getRecords();
        if (records == null || records.isEmpty()) {
            return new HomeworkPageData(Collections.emptyList(), pageResult.getTotal(), Collections.emptyMap(), Collections.emptyMap());
        }

        Map<Long, Long> problemCountMap = queryHomeworkProblemCountMap(homeworkProblemMapper, records);
        Map<Long, Long> classCountMap = queryHomeworkClassCountMap(homeworkClassMapper, records);
        return new HomeworkPageData(records, pageResult.getTotal(), problemCountMap, classCountMap);
    }

    /**
     * @MethodName buildHomeworkPageVo
     * @Param homeworkMapper
     * @Param homeworkProblemMapper
     * @Param homeworkClassMapper
     * @Param wrapper
     * @Param page
     * @Param pageSize
     * @Param converter
     * @Description 构建作业分页展示对象
     * @Return @return {@link PageVo }<{@link T }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static <T> PageVo<T> buildHomeworkPageVo(HomeworkMapper homeworkMapper,
                                                    HomeworkProblemMapper homeworkProblemMapper,
                                                    HomeworkClassMapper homeworkClassMapper,
                                                    LambdaQueryWrapper<Homework> wrapper,
                                                    int page,
                                                    int pageSize,
                                                    Function<HomeworkPageData, List<T>> converter) {
        HomeworkPageData pageData = queryHomeworkPageData(homeworkMapper, homeworkProblemMapper, homeworkClassMapper, wrapper, page, pageSize);
        if (pageData.records().isEmpty()) {
            return new PageVo<>(Collections.emptyList(), pageData.total());
        }
        return new PageVo<>(converter.apply(pageData), pageData.total());
    }

    /**
     * @MethodName queryHomeworkById
     * @Param homeworkMapper
     * @Param homeworkId
     * @Param notFoundMessage
     * @Description 按 id 查询作业
     * @Return @return {@link Homework }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static Homework queryHomeworkById(HomeworkMapper homeworkMapper, Long homeworkId, String notFoundMessage) {
        if (homeworkId == null || homeworkId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "homeworkId 不合法");
        }
        Homework homework = homeworkMapper.selectById(homeworkId);
        if (homework == null) {
            throw new BizException(ResultCode.NOT_FOUND, notFoundMessage);
        }
        return homework;
    }

    /**
     * @MethodName queryEnabledHomework
     * @Param homeworkMapper
     * @Param homeworkId
     * @Param notFoundMessage
     * @Description 按 id 查询已启用作业
     * @Return @return {@link Homework }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static Homework queryEnabledHomework(HomeworkMapper homeworkMapper, Long homeworkId, String notFoundMessage) {
        if (homeworkId == null || homeworkId <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "homeworkId 不合法");
        }
        Homework homework = homeworkMapper.selectOne(new LambdaQueryWrapper<Homework>()
                .eq(Homework::getId, homeworkId)
                .eq(Homework::getStatus, HomeworkStatusConstant.ENABLED)
                .last("limit 1"));
        if (homework == null) {
            throw new BizException(ResultCode.NOT_FOUND, notFoundMessage);
        }
        return homework;
    }

    /**
     * @MethodName queryHomeworkClassIds
     * @Param homeworkClassMapper
     * @Param homeworkId
     * @Description 查询作业关联班级 id 列表
     * @Return @return {@link List }<{@link Long }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static List<Long> queryHomeworkClassIds(HomeworkClassMapper homeworkClassMapper, Long homeworkId) {
        return homeworkClassMapper.selectList(new LambdaQueryWrapper<HomeworkClass>()
                        .select(HomeworkClass::getClassId)
                        .eq(HomeworkClass::getHid, homeworkId)
                        .orderByAsc(HomeworkClass::getId))
                .stream()
                .map(HomeworkClass::getClassId)
                .toList();
    }

    /**
     * @MethodName queryHomeworkProblems
     * @Param homeworkProblemMapper
     * @Param homeworkId
     * @Description 查询作业关联题目
     * @Return @return {@link List }<{@link HomeworkProblemVo }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static List<HomeworkProblemVo> queryHomeworkProblems(HomeworkProblemMapper homeworkProblemMapper, Long homeworkId) {
        return homeworkProblemMapper.selectList(new LambdaQueryWrapper<HomeworkProblem>()
                        .eq(HomeworkProblem::getHid, homeworkId)
                        .orderByAsc(HomeworkProblem::getDisplayId)
                        .orderByAsc(HomeworkProblem::getId))
                .stream()
                .map(HomeworkServiceSupport::toHomeworkProblemVo)
                .toList();
    }

    /**
     * @MethodName queryHomeworkProblemCountMap
     * @Param homeworkProblemMapper
     * @Param homeworks
     * @Description 查询作业题目数量映射
     * @Return @return {@link Map }<{@link Long }, {@link Long }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static Map<Long, Long> queryHomeworkProblemCountMap(HomeworkProblemMapper homeworkProblemMapper, List<Homework> homeworks) {
        List<Long> homeworkIds = homeworks.stream().map(Homework::getId).toList();
        if (homeworkIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<HomeworkProblem> relations = homeworkProblemMapper.selectList(new LambdaQueryWrapper<HomeworkProblem>()
                .select(HomeworkProblem::getHid)
                .in(HomeworkProblem::getHid, homeworkIds));
        if (relations == null || relations.isEmpty()) {
            return Collections.emptyMap();
        }
        return relations.stream().collect(Collectors.groupingBy(HomeworkProblem::getHid, Collectors.counting()));
    }

    /**
     * @MethodName queryHomeworkClassCountMap
     * @Param homeworkClassMapper
     * @Param homeworks
     * @Description 查询作业班级数量映射
     * @Return @return {@link Map }<{@link Long }, {@link Long }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static Map<Long, Long> queryHomeworkClassCountMap(HomeworkClassMapper homeworkClassMapper, List<Homework> homeworks) {
        List<Long> homeworkIds = homeworks.stream().map(Homework::getId).toList();
        if (homeworkIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<HomeworkClass> relations = homeworkClassMapper.selectList(new LambdaQueryWrapper<HomeworkClass>()
                .select(HomeworkClass::getHid)
                .in(HomeworkClass::getHid, homeworkIds));
        if (relations == null || relations.isEmpty()) {
            return Collections.emptyMap();
        }
        return relations.stream().collect(Collectors.groupingBy(HomeworkClass::getHid, Collectors.counting()));
    }

    /**
     * @MethodName toHomeworkProblemVo
     * @Param problem
     * @Description 实体转题目展示对象
     * @Return @return {@link HomeworkProblemVo }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static HomeworkProblemVo toHomeworkProblemVo(HomeworkProblem problem) {
        HomeworkProblemVo vo = new HomeworkProblemVo();
        vo.setId(problem.getId());
        vo.setProblemId(problem.getProblemId());
        vo.setDisplayId(toDisplayIdCode(problem.getDisplayId()));
        return vo;
    }

    /**
     * @MethodName normalizeDisplayIdCode
     * @Param displayIdCode
     * @Description 标准化 displayId 字母序号
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static String normalizeDisplayIdCode(String displayIdCode) {
        String normalizedCode = trimToNull(displayIdCode);
        if (normalizedCode == null) {
            return null;
        }
        normalizedCode = normalizedCode.toUpperCase();
        if (!DISPLAY_ID_PATTERN.matcher(normalizedCode).matches()) {
            throw new BizException(ResultCode.BAD_REQUEST, "displayId 仅支持字母序号，如 A、B、AA");
        }
        return normalizedCode;
    }

    /**
     * @MethodName toDisplayIdNumber
     * @Param displayIdCode
     * @Description 字母序号转数据库整数序号
     * @Return @return {@link Integer }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static Integer toDisplayIdNumber(String displayIdCode) {
        String normalizedCode = normalizeDisplayIdCode(displayIdCode);
        if (normalizedCode == null) {
            return null;
        }
        long value = 0L;
        for (int i = 0; i < normalizedCode.length(); i++) {
            char currentChar = normalizedCode.charAt(i);
            int currentValue = currentChar - DISPLAY_ID_FIRST_CHAR + DISPLAY_ID_MIN_VALUE;
            value = value * DISPLAY_ID_RADIX + currentValue;
            if (value > Integer.MAX_VALUE) {
                throw new BizException(ResultCode.BAD_REQUEST, "displayId 超出允许范围");
            }
        }
        return (int) value;
    }

    /**
     * @MethodName toDisplayIdCode
     * @Param displayId
     * @Description 数据库整数序号转字母序号
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static String toDisplayIdCode(Integer displayId) {
        if (displayId == null || displayId <= 0) {
            return null;
        }
        int value = displayId;
        StringBuilder builder = new StringBuilder();
        while (value > 0) {
            int remainder = (value - DISPLAY_ID_MIN_VALUE) % DISPLAY_ID_RADIX;
            builder.append((char) (DISPLAY_ID_FIRST_CHAR + remainder));
            value = (value - DISPLAY_ID_MIN_VALUE) / DISPLAY_ID_RADIX;
        }
        return builder.reverse().toString();
    }

    /**
     * @MethodName buildDisplayIdByIndex
     * @Param index
     * @Description 根据索引构建 displayId（0->A, 1->B, 26->AA）
     * @Return @return {@link String }
     * @Author HaoRan_Lyu
     * @Date 2026/02/28
     */
    public static String buildDisplayIdByIndex(int index) {
        if (index < 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "displayId 索引不合法");
        }
        return toDisplayIdCode(index + DISPLAY_ID_MIN_VALUE);
    }

    /**
     * @MethodName toLocalDateTime
     * @Param epochMillis
     * @Description 毫秒时间戳转 LocalDateTime
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

    /**
     * 作业分页查询结果
     */
    public record HomeworkPageData(List<Homework> records,
                                   long total,
                                   Map<Long, Long> problemCountMap,
                                   Map<Long, Long> classCountMap) {
    }
}
