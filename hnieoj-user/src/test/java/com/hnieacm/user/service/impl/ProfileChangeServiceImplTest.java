package com.hnieacm.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.constant.AuthCacheConstant;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.user.constant.ProfileChangeStatusConstant;
import com.hnieacm.user.dto.ProfileChangeCreateRequest;
import com.hnieacm.user.entity.SysClass;
import com.hnieacm.user.entity.SysCollege;
import com.hnieacm.user.entity.UserInfo;
import com.hnieacm.user.entity.UserProfileChange;
import com.hnieacm.user.mapper.SysClassMapper;
import com.hnieacm.user.mapper.SysCollegeMapper;
import com.hnieacm.user.mapper.UserInfoMapper;
import com.hnieacm.user.mapper.UserProfileChangeMapper;
import com.hnieacm.user.support.MyBatisPlusTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 身份资料变更申请服务回归：用户行锁、重复待审冲突、原值一致性、幂等/反向审核拒绝、提交后清缓存。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileChangeServiceImplTest {

    private static final String ORIGINAL_MATCHED =
            "{\"realname\":\"Old\",\"collegeId\":1,\"grade\":\"2024\",\"classId\":10}";
    private static final String PROPOSED_VALID =
            "{\"realname\":\"New\",\"collegeId\":1,\"grade\":\"2024\",\"classId\":10}";

    @Mock
    private UserProfileChangeMapper userProfileChangeMapper;

    @Mock
    private UserInfoMapper userInfoMapper;

    @Mock
    private SysCollegeMapper sysCollegeMapper;

    @Mock
    private SysClassMapper sysClassMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private ProfileChangeServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MyBatisPlusTestSupport.initTableInfo(
                UserInfo.class, UserProfileChange.class, SysClass.class, SysCollege.class);
    }

    @BeforeEach
    void setUp() {
        service = new ProfileChangeServiceImpl(
                userProfileChangeMapper, userInfoMapper, sysCollegeMapper, sysClassMapper,
                stringRedisTemplate, new ObjectMapper());
    }

    @Test
    void createLocksUserRowAndRejectsExistingPending() {
        when(userInfoMapper.selectOne(any())).thenReturn(user("u1", "Old", 1L, "2024", 10L));
        when(userProfileChangeMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.createChangeRequest("u1", validRequest()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("待审核");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<UserInfo>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(userInfoMapper).selectOne(captor.capture());
        assertThat(captor.getValue().getTargetSql()).contains("FOR UPDATE");
        verify(userProfileChangeMapper, never()).insert(any(UserProfileChange.class));
    }

    @Test
    void createValidatesFullIdentityAndSavesOriginalAndProposed() {
        when(userInfoMapper.selectOne(any())).thenReturn(user("u1", "Old", 1L, "2024", 10L));
        when(userProfileChangeMapper.selectCount(any())).thenReturn(0L);
        stubValidIdentity();

        service.createChangeRequest("u1", validRequest());

        ArgumentCaptor<UserProfileChange> captor = ArgumentCaptor.forClass(UserProfileChange.class);
        verify(userProfileChangeMapper).insert(captor.capture());
        UserProfileChange saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ProfileChangeStatusConstant.PENDING);
        assertThat(saved.getOriginal()).contains("\"realname\":\"Old\"");
        assertThat(saved.getProposed()).contains("\"realname\":\"New\"");
        assertThat(saved.getReason()).isEqualTo("搬家");
    }

    @Test
    void createRejectsClassCollegeMismatch() {
        when(userInfoMapper.selectOne(any())).thenReturn(user("u1", "Old", 1L, "2024", 10L));
        when(userProfileChangeMapper.selectCount(any())).thenReturn(0L);
        SysCollege college = new SysCollege();
        college.setId(1L);
        when(sysCollegeMapper.selectById(1L)).thenReturn(college);
        SysClass sysClass = new SysClass();
        sysClass.setId(10L);
        sysClass.setCollegeId(2L);
        sysClass.setGrade("2024");
        when(sysClassMapper.selectById(10L)).thenReturn(sysClass);

        assertThatThrownBy(() -> service.createChangeRequest("u1", validRequest()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("班级与学院不匹配");
        verify(userProfileChangeMapper, never()).insert(any(UserProfileChange.class));
    }

    @Test
    void myListFiltersByLoginUid() {
        Page<UserProfileChange> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);
        when(userProfileChangeMapper.selectPage(any(), any())).thenReturn(page);

        service.listMyChangeRequests("u1", 1, 10);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<UserProfileChange>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(userProfileChangeMapper).selectPage(any(), captor.capture());
        assertThat(captor.getValue().getTargetSql()).contains("uid");
    }

    @Test
    void adminListRejectsUnknownStatus() {
        assertThatThrownBy(() -> service.listAdminChangeRequests(1, 10, "UNKNOWN", null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("status");
    }

    @Test
    void approveRejectsWhenOriginalNoLongerMatches() {
        UserProfileChange change = change(1L, "u1", ProfileChangeStatusConstant.PENDING,
                ORIGINAL_MATCHED, PROPOSED_VALID);
        when(userProfileChangeMapper.selectOne(any())).thenReturn(change);
        when(userInfoMapper.selectOne(any())).thenReturn(user("u1", "Changed", 1L, "2024", 10L));

        assertThatThrownBy(() -> service.approve(1L, null, "admin"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已发生变化");

        verify(userInfoMapper, never()).updateById(any(UserInfo.class));
        verify(userProfileChangeMapper, never()).updateById(any(UserProfileChange.class));
    }

    @Test
    void approveAppliesIdentityAndReviewInfo() {
        UserProfileChange change = change(1L, "u1", ProfileChangeStatusConstant.PENDING,
                ORIGINAL_MATCHED, PROPOSED_VALID);
        when(userProfileChangeMapper.selectOne(any())).thenReturn(change);
        UserInfo user = user("u1", "Old", 1L, "2024", 10L);
        when(userInfoMapper.selectOne(any())).thenReturn(user);
        stubValidIdentity();

        service.approve(1L, "ok", "admin");

        assertThat(user.getRealname()).isEqualTo("New");
        assertThat(change.getStatus()).isEqualTo(ProfileChangeStatusConstant.APPROVED);
        assertThat(change.getReviewerUid()).isEqualTo("admin");
        assertThat(change.getReviewAt()).isNotNull();
        verify(userInfoMapper).updateById(user);
        verify(userProfileChangeMapper).updateById(change);
    }

    @Test
    void approveOnAlreadyApprovedIsIdempotent() {
        UserProfileChange change = change(2L, "u1", ProfileChangeStatusConstant.APPROVED,
                ORIGINAL_MATCHED, PROPOSED_VALID);
        when(userProfileChangeMapper.selectOne(any())).thenReturn(change);

        service.approve(2L, null, "admin");

        verify(userInfoMapper, never()).selectOne(any());
        verify(userInfoMapper, never()).updateById(any(UserInfo.class));
        verify(userProfileChangeMapper, never()).updateById(any(UserProfileChange.class));
    }

    @Test
    void approveOnRejectedIsReverseReviewRejected() {
        UserProfileChange change = change(3L, "u1", ProfileChangeStatusConstant.REJECTED,
                ORIGINAL_MATCHED, PROPOSED_VALID);
        when(userProfileChangeMapper.selectOne(any())).thenReturn(change);

        assertThatThrownBy(() -> service.approve(3L, null, "admin"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已驳回");
    }

    @Test
    void rejectRequiresReasonAndIsIdempotent() {
        UserProfileChange pending = change(4L, "u1", ProfileChangeStatusConstant.PENDING,
                ORIGINAL_MATCHED, PROPOSED_VALID);
        when(userProfileChangeMapper.selectOne(any())).thenReturn(pending);

        assertThatThrownBy(() -> service.reject(4L, "  ", "admin"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("驳回原因");
        verify(userProfileChangeMapper, never()).updateById(any(UserProfileChange.class));

        pending.setStatus(ProfileChangeStatusConstant.REJECTED);
        service.reject(4L, "again", "admin");
        verify(userProfileChangeMapper, never()).updateById(any(UserProfileChange.class));
    }

    @Test
    void rejectOnApprovedIsReverseReviewRejected() {
        UserProfileChange change = change(5L, "u1", ProfileChangeStatusConstant.APPROVED,
                ORIGINAL_MATCHED, PROPOSED_VALID);
        when(userProfileChangeMapper.selectOne(any())).thenReturn(change);

        assertThatThrownBy(() -> service.reject(5L, "no", "admin"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已通过");
    }

    @Test
    void approveClearsAuthCacheOnlyAfterCommit() {
        UserProfileChange change = change(6L, "u1", ProfileChangeStatusConstant.PENDING,
                ORIGINAL_MATCHED, PROPOSED_VALID);
        when(userProfileChangeMapper.selectOne(any())).thenReturn(change);
        when(userInfoMapper.selectOne(any())).thenReturn(user("u1", "Old", 1L, "2024", 10L));
        stubValidIdentity();

        TransactionTemplate template = new TransactionTemplate(new StubTransactionManager());
        template.executeWithoutResult(status -> {
            service.approve(6L, null, "admin");
            verify(stringRedisTemplate, never()).delete(any(String.class));
        });

        verify(stringRedisTemplate, times(1)).delete(AuthCacheConstant.ROLE_CACHE_PREFIX + "u1");
        verify(stringRedisTemplate, times(1)).delete(AuthCacheConstant.PERMISSION_CACHE_PREFIX + "u1");
    }

    private void stubValidIdentity() {
        SysCollege college = new SysCollege();
        college.setId(1L);
        when(sysCollegeMapper.selectById(1L)).thenReturn(college);
        SysClass sysClass = new SysClass();
        sysClass.setId(10L);
        sysClass.setCollegeId(1L);
        sysClass.setGrade("2024");
        when(sysClassMapper.selectById(10L)).thenReturn(sysClass);
        when(sysClassMapper.selectCount(any())).thenReturn(1L);
    }

    private ProfileChangeCreateRequest validRequest() {
        ProfileChangeCreateRequest request = new ProfileChangeCreateRequest();
        request.setRealname("New");
        request.setCollegeId(1L);
        request.setGrade("2024");
        request.setClassId(10L);
        request.setReason("搬家");
        return request;
    }

    private UserProfileChange change(Long id, String uid, String status, String original, String proposed) {
        UserProfileChange change = new UserProfileChange();
        change.setId(id);
        change.setUid(uid);
        change.setStatus(status);
        change.setOriginal(original);
        change.setProposed(proposed);
        change.setReason("reason");
        return change;
    }

    private UserInfo user(String uid, String realname, Long collegeId, String grade, Long classId) {
        UserInfo user = new UserInfo();
        user.setUuid("uuid-" + uid);
        user.setUid(uid);
        user.setRealname(realname);
        user.setCollegeId(collegeId);
        user.setGrade(grade);
        user.setClassId(classId);
        return user;
    }

    private static final class StubTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            assertThat(transaction).isNotNull();
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
