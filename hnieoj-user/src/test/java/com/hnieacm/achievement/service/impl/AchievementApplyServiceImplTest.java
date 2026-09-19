package com.hnieacm.achievement.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hnieacm.achievement.constant.AchievementApplyStatus;
import com.hnieacm.achievement.entity.AchievementApply;
import com.hnieacm.achievement.entity.UserAchievement;
import com.hnieacm.achievement.entity.UserInfo;
import com.hnieacm.achievement.mapper.AchievementApplyMapper;
import com.hnieacm.achievement.mapper.UserAchievementMapper;
import com.hnieacm.achievement.mapper.UserInfoMapper;
import com.hnieacm.achievement.service.AchievementFileService;
import com.hnieacm.achievement.vo.BatchAchievementApplyResultVo;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 成就认证申请服务测试
 */
@ExtendWith(MockitoExtension.class)
class AchievementApplyServiceImplTest {

    @Mock
    private AchievementApplyMapper achievementApplyMapper;

    @Mock
    private UserAchievementMapper userAchievementMapper;

    @Mock
    private UserInfoMapper userInfoMapper;

    @Mock
    private AchievementFileService achievementFileService;

    @BeforeEach
    void setUp() {
        initTableInfo(UserInfo.class);
    }

    @Test
    void shouldBatchApproveWithDeduplicationAndPartialFailure() {
        AchievementApplyServiceImpl service = new AchievementApplyServiceImpl(
                achievementApplyMapper, userAchievementMapper, userInfoMapper, achievementFileService);
        when(achievementApplyMapper.selectById(1L)).thenReturn(apply(1L, "u1", AchievementApplyStatus.PENDING));
        when(achievementApplyMapper.selectById(2L)).thenReturn(apply(2L, "u2", AchievementApplyStatus.REJECTED));
        when(userInfoMapper.selectCount(any())).thenReturn(1L);

        BatchAchievementApplyResultVo result = service.batchApprove(Arrays.asList(1L, 2L, 1L, null, -1L));

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getFailures()).singleElement()
                .satisfies(item -> assertThat(item.getId()).isEqualTo(2L));
        verify(userAchievementMapper, times(1)).insert(any(UserAchievement.class));
        verify(achievementApplyMapper, times(1)).updateById(any(AchievementApply.class));
    }

    private AchievementApply apply(Long id, String uid, String status) {
        AchievementApply apply = new AchievementApply();
        apply.setId(id);
        apply.setUid(uid);
        apply.setTitle("ACM");
        apply.setDescription("获奖");
        apply.setFileUrl("/proof.png");
        apply.setStatus(status);
        return apply;
    }

    private void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
