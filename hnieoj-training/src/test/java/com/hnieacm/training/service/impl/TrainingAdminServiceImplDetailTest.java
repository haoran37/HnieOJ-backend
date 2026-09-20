package com.hnieacm.training.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.training.constant.TrainingAuthConstant;
import com.hnieacm.training.constant.TrainingStatusConstant;
import com.hnieacm.training.constant.TrainingTypeConstant;
import com.hnieacm.training.entity.Training;
import com.hnieacm.training.entity.TrainingProblem;
import com.hnieacm.training.mapper.TrainingCategoryRelMapper;
import com.hnieacm.training.mapper.TrainingMapper;
import com.hnieacm.training.mapper.TrainingProblemMapper;
import com.hnieacm.training.service.manager.TrainingProblemManager;
import com.hnieacm.training.vo.AdminTrainingDetailVo;
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
 * @Description: 管理端完整题单详情回归：停用/私有题单必须返回全部可编辑字段与有序题目，
 * 且 problems 仅暴露 problemId/displayId；不存在时返回 NOT_FOUND 业务码。
 */
class TrainingAdminServiceImplDetailTest {

    private TrainingMapper trainingMapper;
    private TrainingProblemMapper trainingProblemMapper;
    private TrainingAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        trainingMapper = mock(TrainingMapper.class);
        trainingProblemMapper = mock(TrainingProblemMapper.class);
        service = new TrainingAdminServiceImpl(
                trainingMapper,
                trainingProblemMapper,
                mock(TrainingCategoryRelMapper.class),
                mock(TrainingProblemManager.class)
        );
    }

    @Test
    void returnsAllEditableFieldsIncludingPrivatePwdForDisabledTraining() {
        Training training = new Training();
        training.setId(42L);
        training.setTitle("Disabled private training");
        training.setType(TrainingTypeConstant.OFFICIAL);
        training.setAuth(TrainingAuthConstant.PRIVATE);
        training.setPrivatePwd("secret-pwd");
        training.setDescription("retained description");
        training.setStatus(TrainingStatusConstant.DISABLED);
        training.setRank(7);
        when(trainingMapper.selectById(42L)).thenReturn(training);

        TrainingProblem first = new TrainingProblem();
        first.setTid(42L);
        first.setProblemId(8L);
        first.setDisplayId(3);
        TrainingProblem second = new TrainingProblem();
        second.setTid(42L);
        second.setProblemId(9L);
        second.setDisplayId(1);
        when(trainingProblemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(first, second));

        AdminTrainingDetailVo detail = service.getTrainingDetail(42L);

        assertThat(detail.getId()).isEqualTo(42L);
        assertThat(detail.getTitle()).isEqualTo("Disabled private training");
        assertThat(detail.getType()).isEqualTo(TrainingTypeConstant.OFFICIAL);
        assertThat(detail.getAuth()).isEqualTo(TrainingAuthConstant.PRIVATE);
        assertThat(detail.getPrivatePwd()).isEqualTo("secret-pwd");
        assertThat(detail.getDescription()).isEqualTo("retained description");
        assertThat(detail.getStatus()).isFalse();
        assertThat(detail.getRank()).isEqualTo(7);
        assertThat(detail.getProblems()).hasSize(2);
        assertThat(detail.getProblems().get(0).getProblemId()).isEqualTo(8L);
        assertThat(detail.getProblems().get(0).getDisplayId()).isEqualTo(3);
        assertThat(detail.getProblems().get(1).getProblemId()).isEqualTo(9L);
        assertThat(detail.getProblems().get(1).getDisplayId()).isEqualTo(1);
    }

    @Test
    void missingTrainingReturnsNotFoundBusinessCode() {
        when(trainingMapper.selectById(999999999L)).thenReturn(null);

        assertThatThrownBy(() -> service.getTrainingDetail(999999999L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.NOT_FOUND);
    }

    @Test
    void serializedProblemsExposeOnlyProblemIdAndDisplayId() throws Exception {
        AdminTrainingDetailVo vo = new AdminTrainingDetailVo();
        vo.setId(42L);
        vo.setTitle("t");
        vo.setType(TrainingTypeConstant.OFFICIAL);
        vo.setAuth(TrainingAuthConstant.PRIVATE);
        vo.setPrivatePwd("pwd");
        vo.setDescription("d");
        vo.setStatus(false);
        vo.setRank(7);
        AdminTrainingDetailVo.Problem problem = new AdminTrainingDetailVo.Problem();
        problem.setProblemId(8L);
        problem.setDisplayId(3);
        vo.setProblems(List.of(problem));

        JsonNode node = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(vo));

        assertThat(node.get("id").asLong()).isEqualTo(42L);
        assertThat(node.get("privatePwd").asText()).isEqualTo("pwd");
        assertThat(node.get("status").asBoolean()).isFalse();
        JsonNode problems = node.get("problems");
        assertThat(problems.size()).isEqualTo(1);
        assertThat(problems.get(0).size()).isEqualTo(2);
        assertThat(problems.get(0).get("problemId").asLong()).isEqualTo(8L);
        assertThat(problems.get(0).get("displayId").asInt()).isEqualTo(3);
    }
}
