package com.hnieacm.contest.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.contest.constant.ContestListWindowConstant;
import com.hnieacm.contest.dto.ContestListQuery;
import com.hnieacm.contest.entity.Contest;
import com.hnieacm.contest.mapper.ContestMapper;
import com.hnieacm.contest.mapper.ContestProblemMapper;
import com.hnieacm.contest.vo.ContestListVo;
import com.hnieacm.common.dto.PageVo;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/21
 * @Description: 比赛列表时间筛选与 window=recent 排序回归。
 * <p>枚举非法参数、时间窗过滤与「距当前由近到远」排序片段都在这里锁定；
 * 真实库上的端点行为由集成验收覆盖。</p>
 */
class ContestQueryServiceImplListContestsTest {

    private ContestMapper contestMapper;
    private ContestQueryServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper 需要 Contest 的 TableInfo 才能把方法引用解析成列名
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "contest-list-test"), Contest.class);
    }

    @BeforeEach
    void setUp() {
        contestMapper = mock(ContestMapper.class);
        service = new ContestQueryServiceImpl(
                contestMapper,
                mock(ContestProblemMapper.class),
                mock(com.hnieacm.contest.mapper.ContestRegisterMapper.class),
                new ObjectMapper());
    }

    @Test
    void defaultOrderIsStartTimeDescending() {
        stubEmptyPage();

        service.listContests(query(null, null, null));

        String sql = capturedSql();
        assertThat(sql).containsIgnoringCase("ORDER BY start_time DESC");
        assertThat(sql).doesNotContain("TIMESTAMPDIFF");
    }

    @Test
    void recentWindowOrdersByDistanceToNowInsteadOfLatestStart() {
        stubEmptyPage();

        service.listContests(query(null, null, ContestListWindowConstant.RECENT));

        String sql = capturedSql();
        // 关键：不再按 start_time 倒序，否则会拿到「开始时间最晚」而不是「距当前最近」的那场
        assertThat(sql).doesNotContain("ORDER BY start_time DESC");
        assertThat(sql).contains("ORDER BY ABS(TIMESTAMPDIFF(SECOND, '");
        assertThat(sql).contains("', start_time)) ASC, id ASC");
    }

    @Test
    void recentWindowPivotUsesJvmClockAtSecondPrecision() {
        stubEmptyPage();
        LocalDateTime before = LocalDateTime.now().minusSeconds(5);

        service.listContests(query(null, null, ContestListWindowConstant.RECENT));

        LocalDateTime after = LocalDateTime.now().plusSeconds(5);
        String pivot = pivotFrom(capturedSql());
        LocalDateTime parsed = LocalDateTime.parse(pivot.replace(' ', 'T'));
        // pivot 取自 JVM 时钟（与 resolveRuntimeStatus 同一套时间），不是数据库时钟
        assertThat(parsed).isBetween(before, after);
    }

    @Test
    void startFromAndStartToFilterStartTimeInclusively() {
        stubEmptyPage();
        long from = epochMillis(2026, 1, 1, 0, 0, 0);
        long to = epochMillis(2026, 6, 1, 0, 0, 0);

        service.listContests(query(from, to, null));

        LambdaQueryWrapper<Contest> wrapper = capturedWrapper();
        String sql = wrapper.getTargetSql();
        assertThat(sql).contains("start_time >= ?").contains("start_time <= ?");
        // 只取时间参数：参数表里还有 is_visible 的 1
        assertThat(timeParams(wrapper)).containsExactlyInAnyOrder(
                LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                LocalDateTime.of(2026, 6, 1, 0, 0, 0));
    }

    @Test
    void onlyStartFromFiltersLowerBound() {
        stubEmptyPage();

        service.listContests(query(epochMillis(2026, 1, 1, 0, 0, 0), null, null));

        String sql = capturedSql();
        assertThat(sql).contains("start_time >= ?");
        assertThat(sql).doesNotContain("start_time <= ?");
    }

    @Test
    void timeWindowCombinesWithRecentOrdering() {
        stubEmptyPage();

        service.listContests(query(epochMillis(2026, 1, 1, 0, 0, 0), null, ContestListWindowConstant.RECENT));

        String sql = capturedSql();
        // 时间窗只做过滤，recent 只改排序，两者叠加
        assertThat(sql).contains("start_time >= ?");
        assertThat(sql).contains("ABS(TIMESTAMPDIFF(SECOND, '");
    }

    @Test
    void startFromLaterThanStartToIsRejected() {
        stubEmptyPage();

        assertThatThrownBy(() -> service.listContests(
                query(epochMillis(2026, 6, 1, 0, 0, 0), epochMillis(2026, 1, 1, 0, 0, 0), null)))
                .isInstanceOf(BizException.class)
                .hasMessage("startFrom 不能晚于 startTo")
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST);
    }

    @Test
    void unknownWindowIsRejectedInsteadOfSilentlyIgnored() {
        stubEmptyPage();

        assertThatThrownBy(() -> service.listContests(query(null, null, "recentt")))
                .isInstanceOf(BizException.class)
                .hasMessage("window 参数不合法，仅支持 recent")
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST);
    }

    @Test
    void emptyWindowIsTreatedAsUnset() {
        stubEmptyPage();

        PageVo<ContestListVo> result = service.listContests(query(null, null, "   "));

        assertThat(result).isNotNull();
        assertThat(capturedSql()).containsIgnoringCase("ORDER BY start_time DESC");
    }

    private ContestListQuery query(Long startFrom, Long startTo, String window) {
        return new ContestListQuery(1, 10, null, null, startFrom, startTo, window);
    }

    @SuppressWarnings("unchecked")
    private void stubEmptyPage() {
        when(contestMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<Contest> requested = invocation.getArgument(0);
            requested.setRecords(List.of());
            requested.setTotal(0);
            return requested;
        });
    }

    private String capturedSql() {
        return capturedWrapper().getTargetSql();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private LambdaQueryWrapper<Contest> capturedWrapper() {
        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(contestMapper).selectPage(any(Page.class), captor.capture());
        return captor.getValue();
    }

    private static List<LocalDateTime> timeParams(LambdaQueryWrapper<Contest> wrapper) {
        return wrapper.getParamNameValuePairs().values().stream()
                .filter(LocalDateTime.class::isInstance)
                .map(LocalDateTime.class::cast)
                .toList();
    }

    private static String pivotFrom(String sql) {
        int start = sql.indexOf("TIMESTAMPDIFF(SECOND, '") + "TIMESTAMPDIFF(SECOND, '".length();
        int end = sql.indexOf('\'', start);
        return sql.substring(start, end);
    }

    private static long epochMillis(int year, int month, int day, int hour, int minute, int second) {
        return LocalDateTime.of(year, month, day, hour, minute, second)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
    }
}
