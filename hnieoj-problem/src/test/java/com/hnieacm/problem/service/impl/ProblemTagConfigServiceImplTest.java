package com.hnieacm.problem.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.problem.dto.SaveTagConfigRequest;
import com.hnieacm.problem.dto.TagGroupRequest;
import com.hnieacm.problem.entity.ProblemTag;
import com.hnieacm.problem.entity.Tag;
import com.hnieacm.problem.mapper.ProblemTagMapper;
import com.hnieacm.problem.mapper.TagMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 题目标签配置服务测试
 */
@ExtendWith(MockitoExtension.class)
class ProblemTagConfigServiceImplTest {

    @Mock
    private TagMapper tagMapper;

    @Mock
    private ProblemTagMapper problemTagMapper;

    @BeforeEach
    void setUp() {
        initTableInfo(Tag.class);
        initTableInfo(ProblemTag.class);
    }

    @Test
    void shouldInsertMoveAndDeleteTagsWhenSavingConfig() {
        ProblemTagConfigServiceImpl service = new ProblemTagConfigServiceImpl(tagMapper, problemTagMapper);
        when(tagMapper.selectList(any())).thenReturn(List.of(
                tag(1L, "math", "基础"),
                tag(2L, "dp", "算法")
        ));
        when(problemTagMapper.selectCount(org.mockito.ArgumentMatchers.<Wrapper<ProblemTag>>any())).thenReturn(0L);

        service.save(request(group("算法", List.of("math", "graph"))));

        ArgumentCaptor<Tag> tagCaptor = ArgumentCaptor.forClass(Tag.class);
        verify(tagMapper).insert(tagCaptor.capture());
        assertThat(tagCaptor.getValue().getName()).isEqualTo("graph");
        assertThat(tagCaptor.getValue().getCategory()).isEqualTo("算法");
        verify(tagMapper).update(org.mockito.ArgumentMatchers.isNull(), any());
        verify(tagMapper).deleteById(2L);
    }

    @Test
    void shouldRejectRemovingUsedTag() {
        ProblemTagConfigServiceImpl service = new ProblemTagConfigServiceImpl(tagMapper, problemTagMapper);
        when(tagMapper.selectList(any())).thenReturn(List.of(tag(1L, "dp", "算法")));
        when(problemTagMapper.selectCount(org.mockito.ArgumentMatchers.<Wrapper<ProblemTag>>any())).thenReturn(1L);

        assertThatThrownBy(() -> service.save(request(group("算法", List.of("graph")))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("标签已被题目使用");
    }

    private SaveTagConfigRequest request(TagGroupRequest... groups) {
        SaveTagConfigRequest request = new SaveTagConfigRequest();
        request.setTags(List.of(groups));
        return request;
    }

    private TagGroupRequest group(String title, List<String> tags) {
        TagGroupRequest group = new TagGroupRequest();
        group.setTitle(title);
        group.setTags(tags);
        return group;
    }

    private Tag tag(Long id, String name, String category) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName(name);
        tag.setCategory(category);
        return tag;
    }

    private void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
