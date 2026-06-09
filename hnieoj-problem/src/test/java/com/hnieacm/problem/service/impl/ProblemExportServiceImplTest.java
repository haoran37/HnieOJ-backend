package com.hnieacm.problem.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.problem.entity.Problem;
import com.hnieacm.problem.entity.ProblemTag;
import com.hnieacm.problem.entity.Tag;
import com.hnieacm.problem.mapper.ProblemMapper;
import com.hnieacm.problem.mapper.ProblemTagMapper;
import com.hnieacm.problem.mapper.TagMapper;
import com.hnieacm.problem.service.ProblemFileStorageService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 题目导出服务测试
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class ProblemExportServiceImplTest {

    private ProblemMapper problemMapper;

    private ProblemTagMapper problemTagMapper;

    private TagMapper tagMapper;

    private ProblemFileStorageService problemFileStorageService;

    private ProblemExportServiceImpl service;

    @BeforeEach
    void setUp() {
        initTableInfo(Problem.class);
        initTableInfo(ProblemTag.class);
        initTableInfo(Tag.class);
        problemMapper = mock(ProblemMapper.class);
        problemTagMapper = mock(ProblemTagMapper.class);
        tagMapper = mock(TagMapper.class);
        problemFileStorageService = mock(ProblemFileStorageService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        service = new ProblemExportServiceImpl(
                problemMapper,
                problemTagMapper,
                tagMapper,
                problemFileStorageService,
                objectMapper
        );
    }

    @Test
    void shouldExportManifestZip() throws Exception {
        Problem problem = new Problem();
        problem.setId(1L);
        problem.setProblemCode("P1000");
        problem.setTitle("A+B");
        when(problemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(problem));
        when(problemTagMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(problemFileStorageService.hasAvailableTestdata(1L)).thenReturn(false);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        service.exportProblems(List.of(1L), outputStream);

        assertThat(outputStream.size()).isGreaterThan(0);
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(outputStream.toByteArray()))) {
            ZipEntry entry = zipInputStream.getNextEntry();
            assertThat(entry.getName()).isEqualTo("manifest.json");
        }
    }

    @Test
    void shouldRejectMissingProblem() {
        when(problemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        assertThatThrownBy(() -> service.exportProblems(List.of(1L), outputStream))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("题目不存在");
    }

    private void initTableInfo(Class<?> entityClass) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
