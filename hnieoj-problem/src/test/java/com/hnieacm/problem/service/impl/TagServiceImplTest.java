package com.hnieacm.problem.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.problem.dto.TagCreateRequest;
import com.hnieacm.problem.dto.TagUpdateRequest;
import com.hnieacm.problem.entity.ProblemTag;
import com.hnieacm.problem.entity.Tag;
import com.hnieacm.problem.mapper.ProblemTagMapper;
import com.hnieacm.problem.mapper.TagMapper;
import com.hnieacm.problem.vo.TagVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 标签目录/管理回归：trim、长度、重名、未知 id、被引用删除冲突。
 */
class TagServiceImplTest {

    static {
        // 纯单元测试没有 MyBatis 会话，手动初始化 Tag 的 TableInfo 以便检查 LambdaWrapper 生成的 SQL
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Tag.class);
    }

    private TagMapper tagMapper;
    private ProblemTagMapper problemTagMapper;
    private TagServiceImpl service;

    @BeforeEach
    void setUp() {
        tagMapper = mock(TagMapper.class);
        problemTagMapper = mock(ProblemTagMapper.class);
        service = new TagServiceImpl(tagMapper, problemTagMapper);
    }

    @Test
    void listTagsMapsIdNameColorCategory() {
        Tag tag = new Tag();
        tag.setId(3L);
        tag.setName("dp");
        tag.setColor("#fff");
        tag.setCategory("基础");
        when(tagMapper.selectList(any())).thenReturn(List.of(tag));

        List<TagVo> tags = service.listTags();

        assertThat(tags).hasSize(1);
        assertThat(tags.get(0).getId()).isEqualTo(3L);
        assertThat(tags.get(0).getName()).isEqualTo("dp");
        assertThat(tags.get(0).getColor()).isEqualTo("#fff");
        assertThat(tags.get(0).getCategory()).isEqualTo("基础");
    }

    @Test
    void createTrimsNameColorCategory() {
        when(tagMapper.selectCount(any())).thenReturn(0L);
        TagCreateRequest request = new TagCreateRequest();
        request.setName("  dp  ");
        request.setColor("  #fff  ");
        request.setCategory("  基础  ");

        service.createTag(request);

        ArgumentCaptor<Tag> captor = ArgumentCaptor.forClass(Tag.class);
        verify(tagMapper).insert(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("dp");
        assertThat(captor.getValue().getColor()).isEqualTo("#fff");
        assertThat(captor.getValue().getCategory()).isEqualTo("基础");
    }

    @Test
    void createRejectsDuplicateName() {
        when(tagMapper.selectCount(any())).thenReturn(1L);
        TagCreateRequest request = new TagCreateRequest();
        request.setName("dp");

        assertThatThrownBy(() -> service.createTag(request))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST);
        verify(tagMapper, never()).insert(any(Tag.class));
    }

    @Test
    void createRejectsBlankAndOverlongName() {
        TagCreateRequest blank = new TagCreateRequest();
        blank.setName("   ");
        assertThatThrownBy(() -> service.createTag(blank)).isInstanceOf(BizException.class);

        TagCreateRequest overlong = new TagCreateRequest();
        overlong.setName("a".repeat(51));
        assertThatThrownBy(() -> service.createTag(overlong)).isInstanceOf(BizException.class);
    }

    @Test
    void updateUnknownIdReturnsNotFound() {
        when(tagMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.updateTag(99L, new TagUpdateRequest()))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.NOT_FOUND);
    }

    @Test
    void updateRejectsDuplicateName() {
        Tag existing = new Tag();
        existing.setId(1L);
        existing.setName("old");
        when(tagMapper.selectById(1L)).thenReturn(existing);
        when(tagMapper.selectCount(any())).thenReturn(1L);

        TagUpdateRequest request = new TagUpdateRequest();
        request.setName("taken");

        assertThatThrownBy(() -> service.updateTag(1L, request))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST);
        verify(tagMapper, never()).update(any(), any());
    }

    @Test
    void updateSetsAllFieldsIncludingNulls() {
        Tag existing = new Tag();
        existing.setId(1L);
        existing.setName("old");
        when(tagMapper.selectById(1L)).thenReturn(existing);
        when(tagMapper.selectCount(any())).thenReturn(0L);

        TagUpdateRequest request = new TagUpdateRequest();
        request.setName("  new  ");
        request.setColor("  ");
        request.setCategory("");

        service.updateTag(1L, request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Tag>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(tagMapper).update(any(), captor.capture());
        LambdaUpdateWrapper<?> wrapper = (LambdaUpdateWrapper<?>) captor.getValue();
        assertThat(wrapper.getSqlSet()).contains("name").contains("color").contains("category");
    }

    @Test
    void deleteUnknownReturnsNotFound() {
        when(tagMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.deleteTag(5L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.NOT_FOUND);
    }

    @Test
    void deleteReferencedTagReturnsConflictAndKeepsRelations() {
        Tag tag = new Tag();
        tag.setId(5L);
        tag.setName("dp");
        when(tagMapper.selectList(any())).thenReturn(List.of(tag));
        when(problemTagMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> service.deleteTag(5L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST);
        verify(tagMapper, never()).deleteById(any(java.io.Serializable.class));
        verify(problemTagMapper, never()).delete(any());
    }

    @Test
    void deleteUnreferencedTagDeletesRow() {
        Tag tag = new Tag();
        tag.setId(5L);
        tag.setName("dp");
        when(tagMapper.selectList(any())).thenReturn(List.of(tag));
        when(problemTagMapper.selectCount(any())).thenReturn(0L);

        service.deleteTag(5L);

        verify(tagMapper).deleteById(5L);
        verify(problemTagMapper, never()).delete(any());
    }

    @Test
    void deleteLocksTagRowForUpdate() {
        Tag tag = new Tag();
        tag.setId(5L);
        when(tagMapper.selectList(any())).thenReturn(List.of(tag));
        when(problemTagMapper.selectCount(any())).thenReturn(0L);

        service.deleteTag(5L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Tag>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(tagMapper).selectList(captor.capture());
        assertThat(captor.getValue().getTargetSql()).contains("FOR UPDATE");
    }
}
