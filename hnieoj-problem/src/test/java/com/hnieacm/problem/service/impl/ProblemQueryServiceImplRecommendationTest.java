package com.hnieacm.problem.service.impl;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.constant.PermissionConstant;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.problem.constant.ProblemAuthConstant;
import com.hnieacm.problem.entity.Problem;
import com.hnieacm.problem.entity.ProblemTag;
import com.hnieacm.problem.entity.Tag;
import com.hnieacm.problem.mapper.ProblemMapper;
import com.hnieacm.problem.mapper.ProblemTagMapper;
import com.hnieacm.problem.mapper.TagMapper;
import com.hnieacm.problem.vo.ProblemListVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 推荐题目回归：limit 边界、缺失源、公开候选顺序与标签、私有源无权限拒绝/有权限放行。
 * SQL 排序规则由 Codex 测试库独立验收，这里校验服务层参数化与可见性。
 */
class ProblemQueryServiceImplRecommendationTest {

    private ProblemMapper problemMapper;
    private ProblemTagMapper problemTagMapper;
    private TagMapper tagMapper;
    private ProblemQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        problemMapper = mock(ProblemMapper.class);
        problemTagMapper = mock(ProblemTagMapper.class);
        tagMapper = mock(TagMapper.class);
        service = new ProblemQueryServiceImpl(problemMapper, problemTagMapper, tagMapper, new ObjectMapper(),
                mock(com.hnieacm.problem.feign.ContestAccessFeignClient.class));
        // 默认无任何权限，避免跨测试污染 Sa-Token 全局 StpInterface
        SaManager.setStpInterface(stpInterface(List.of()));
    }

    @AfterEach
    void tearDown() {
        SaTokenContextMockUtil.clearContext();
    }

    private static StpInterface stpInterface(List<String> permissions) {
        return new StpInterface() {
            @Override
            public List<String> getPermissionList(Object loginId, String loginType) {
                return permissions;
            }

            @Override
            public List<String> getRoleList(Object loginId, String loginType) {
                return List.of();
            }
        };
    }

    private Problem problem(long id, int auth, Integer difficulty) {
        Problem problem = new Problem();
        problem.setId(id);
        problem.setProblemCode("P" + id);
        problem.setTitle("Problem " + id);
        problem.setAuth(auth);
        problem.setDifficulty(difficulty);
        problem.setSubmissionCount(10);
        problem.setAcceptedCount(5);
        problem.setScorePercentage(new BigDecimal("50.00"));
        return problem;
    }

    @Test
    void rejectsLimitAtOrBelowZeroAndAboveTen() {
        assertThatThrownBy(() -> service.getRecommendations("P1", 0))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST);
        assertThatThrownBy(() -> service.getRecommendations("P1", -3))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST);
        assertThatThrownBy(() -> service.getRecommendations("P1", 11))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST);
        verifyNoInteractions(problemMapper);
    }

    @Test
    void missingSourceReturnsProblemNotFound() {
        when(problemMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.getRecommendations("MISSING", 5))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.PROBLEM_NOT_FOUND);
    }

    @Test
    void publicSourcePassesPublicAuthAndDifficultyAndKeepsCandidateOrder() {
        Problem source = problem(9L, ProblemAuthConstant.PUBLIC, 1);
        when(problemMapper.selectOne(any())).thenReturn(source);

        Problem candidateA = problem(2L, ProblemAuthConstant.PUBLIC, 1);
        Problem candidateB = problem(3L, ProblemAuthConstant.PUBLIC, 2);
        when(problemMapper.selectRecommendations(any(), anyInt(), any(), anyInt()))
                .thenReturn(List.of(candidateA, candidateB));

        ProblemTag relA = new ProblemTag();
        relA.setProblemId(2L);
        relA.setTid(11L);
        ProblemTag relB = new ProblemTag();
        relB.setProblemId(3L);
        relB.setTid(12L);
        when(problemTagMapper.selectList(any())).thenReturn(List.of(relA, relB));
        Tag tagA = new Tag();
        tagA.setId(11L);
        tagA.setName("dp");
        Tag tagB = new Tag();
        tagB.setId(12L);
        tagB.setName("math");
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of(tagA, tagB));

        List<ProblemListVo> result = service.getRecommendations("P9", 5);

        assertThat(result).extracting(ProblemListVo::getId).containsExactly(2L, 3L);
        assertThat(result.get(0).getTags()).containsExactly("dp");
        assertThat(result.get(1).getTags()).containsExactly("math");
        verify(problemMapper).selectRecommendations(eq(9L), eq(ProblemAuthConstant.PUBLIC), eq(1), eq(5));
    }

    @Test
    void emptyCandidatesReturnEmptyList() {
        when(problemMapper.selectOne(any())).thenReturn(problem(9L, ProblemAuthConstant.PUBLIC, 1));
        when(problemMapper.selectRecommendations(any(), anyInt(), any(), anyInt())).thenReturn(List.of());

        assertThat(service.getRecommendations("P9", 10)).isEmpty();
    }

    @Test
    void privateSourceWithoutProblemUpdatePermissionIsForbidden() {
        when(problemMapper.selectOne(any())).thenReturn(problem(9L, ProblemAuthConstant.PRIVATE, 1));
        SaManager.setStpInterface(stpInterface(List.of()));

        SaTokenContextMockUtil.setMockContext(() -> {
            StpUtil.login("student");
            assertThatThrownBy(() -> service.getRecommendations("P9", 5))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ResultCode.FORBIDDEN);
        });

        verify(problemMapper, never()).selectRecommendations(any(), anyInt(), any(), anyInt());
    }

    @Test
    void privateSourceWithProblemUpdatePermissionIsAllowed() {
        when(problemMapper.selectOne(any())).thenReturn(problem(9L, ProblemAuthConstant.PRIVATE, 1));
        when(problemMapper.selectRecommendations(any(), anyInt(), any(), anyInt())).thenReturn(List.of());
        SaManager.setStpInterface(stpInterface(List.of(PermissionConstant.PROBLEM_UPDATE)));

        SaTokenContextMockUtil.setMockContext(() -> {
            StpUtil.login("root");
            assertThat(service.getRecommendations("P9", 5)).isEmpty();
        });

        verify(problemMapper).selectRecommendations(eq(9L), eq(ProblemAuthConstant.PUBLIC), eq(1), eq(5));
    }
}
