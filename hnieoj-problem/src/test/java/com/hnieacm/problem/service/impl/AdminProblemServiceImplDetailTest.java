package com.hnieacm.problem.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.problem.entity.Problem;
import com.hnieacm.problem.entity.ProblemTag;
import com.hnieacm.problem.entity.Tag;
import com.hnieacm.problem.mapper.ProblemMapper;
import com.hnieacm.problem.mapper.ProblemTagMapper;
import com.hnieacm.problem.mapper.TagMapper;
import com.hnieacm.problem.properties.ProblemJudgeAssetProperties;
import com.hnieacm.problem.service.ProblemFileStorageService;
import com.hnieacm.problem.vo.AdminProblemDetailVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 管理端题目完整编辑详情映射回归：全部可编辑字段（含 SPJ/交互题源码与 limits）、
 * examples 数组与标签必须完整回填；内部数字 id 不存在时返回 PROBLEM_NOT_FOUND。
 */
class AdminProblemServiceImplDetailTest {

    private ProblemMapper problemMapper;
    private ProblemTagMapper problemTagMapper;
    private TagMapper tagMapper;
    private AdminProblemServiceImpl service;

    @BeforeEach
    void setUp() {
        problemMapper = mock(ProblemMapper.class);
        tagMapper = mock(TagMapper.class);
        problemTagMapper = mock(ProblemTagMapper.class);
        service = new AdminProblemServiceImpl(
                problemMapper,
                tagMapper,
                problemTagMapper,
                new ObjectMapper(),
                mock(ProblemFileStorageService.class),
                new ProblemJudgeAssetProperties()
        );
    }

    @Test
    void returnsAllEditableFieldsIncludingCheckerConfigAndTags() {
        Problem problem = new Problem();
        problem.setId(7L);
        problem.setProblemCode("DETAILIT1");
        problem.setTitle("Full detail");
        problem.setAuthor("20230001");
        problem.setType(0);
        problem.setJudgeMode("default");
        problem.setTimeLimit(1234);
        problem.setMemoryLimit(128);
        problem.setStackLimit(64);
        problem.setDescription("statement");
        problem.setInput("two numbers");
        problem.setOutput("sum");
        problem.setExamples("[{\"input\":\"1 2\",\"output\":\"3\"}]");
        problem.setHint("hint");
        problem.setDifficulty(1);
        problem.setAuth(1);
        problem.setIoScore(100);
        problem.setIsRemote(false);
        problem.setSource("local integration");
        problem.setSpjCode("// checker secret marker");
        problem.setSpjLanguage("cpp");
        problem.setSpjTimeLimit(1500);
        problem.setSpjMemoryLimit(128);
        problem.setSpjStackLimit(32);
        problem.setSpjOutputLimit(2048);
        problem.setSpjProtocol("testlib");
        problem.setInteractorCode("// interactor secret marker");
        problem.setInteractorLanguage("cpp");
        problem.setInteractorTimeLimit(1600);
        problem.setInteractorMemoryLimit(256);
        problem.setInteractorStackLimit(64);
        problem.setInteractorOutputLimit(4096);
        problem.setInteractorProtocol("stdio");
        problem.setIsRemoveEndBlank(false);
        problem.setOpenCaseResult(true);
        when(problemMapper.selectById(7L)).thenReturn(problem);

        ProblemTag rel = new ProblemTag();
        rel.setProblemId(7L);
        rel.setTid(11L);
        when(problemTagMapper.selectList(any())).thenReturn(List.of(rel));
        Tag tag = new Tag();
        tag.setId(11L);
        tag.setName("roundtrip");
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of(tag));

        AdminProblemDetailVo detail = service.getProblemDetail(7L);

        assertThat(detail.getTags()).containsExactly("roundtrip");
        var pr = detail.getProblem();
        assertThat(pr.getId()).isEqualTo(7L);
        assertThat(pr.getProblemCode()).isEqualTo("DETAILIT1");
        assertThat(pr.getTitle()).isEqualTo("Full detail");
        assertThat(pr.getAuthor()).isEqualTo("20230001");
        assertThat(pr.getType()).isEqualTo(0);
        assertThat(pr.getJudgeMode()).isEqualTo("default");
        assertThat(pr.getTimeLimit()).isEqualTo(1234);
        assertThat(pr.getMemoryLimit()).isEqualTo(128);
        assertThat(pr.getStackLimit()).isEqualTo(64);
        assertThat(pr.getDescription()).isEqualTo("statement");
        assertThat(pr.getInput()).isEqualTo("two numbers");
        assertThat(pr.getOutput()).isEqualTo("sum");
        assertThat(pr.getExamples()).hasSize(1);
        assertThat(pr.getExamples().get(0).getInput()).isEqualTo("1 2");
        assertThat(pr.getExamples().get(0).getOutput()).isEqualTo("3");
        assertThat(pr.getHint()).isEqualTo("hint");
        assertThat(pr.getDifficulty()).isEqualTo(1);
        assertThat(pr.getAuth()).isEqualTo(1);
        assertThat(pr.getIoScore()).isEqualTo(100);
        assertThat(pr.getIsRemote()).isFalse();
        assertThat(pr.getSource()).isEqualTo("local integration");
        assertThat(pr.getSpjCode()).isEqualTo("// checker secret marker");
        assertThat(pr.getSpjLanguage()).isEqualTo("cpp");
        assertThat(pr.getSpjTimeLimit()).isEqualTo(1500);
        assertThat(pr.getSpjMemoryLimit()).isEqualTo(128);
        assertThat(pr.getSpjStackLimit()).isEqualTo(32);
        assertThat(pr.getSpjOutputLimit()).isEqualTo(2048);
        assertThat(pr.getSpjProtocol()).isEqualTo("testlib");
        assertThat(pr.getInteractorCode()).isEqualTo("// interactor secret marker");
        assertThat(pr.getInteractorLanguage()).isEqualTo("cpp");
        assertThat(pr.getInteractorTimeLimit()).isEqualTo(1600);
        assertThat(pr.getInteractorMemoryLimit()).isEqualTo(256);
        assertThat(pr.getInteractorStackLimit()).isEqualTo(64);
        assertThat(pr.getInteractorOutputLimit()).isEqualTo(4096);
        assertThat(pr.getInteractorProtocol()).isEqualTo("stdio");
        assertThat(pr.getIsRemoveEndBlank()).isFalse();
        assertThat(pr.getOpenCaseResult()).isTrue();
    }

    @Test
    void missingProblemReturnsProblemNotFoundBusinessCode() {
        when(problemMapper.selectById(999999999L)).thenReturn(null);

        assertThatThrownBy(() -> service.getProblemDetail(999999999L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.PROBLEM_NOT_FOUND);
    }

    @Test
    void blankExamplesReturnEmptyList() {
        Problem problem = new Problem();
        problem.setId(8L);
        problem.setAuth(1);
        when(problemMapper.selectById(8L)).thenReturn(problem);
        when(problemTagMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.getProblemDetail(8L).getProblem().getExamples()).isEmpty();
        assertThat(service.getProblemDetail(8L).getTags()).isEmpty();
    }
}
