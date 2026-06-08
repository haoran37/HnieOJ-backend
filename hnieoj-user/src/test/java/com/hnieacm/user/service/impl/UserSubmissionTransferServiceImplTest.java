package com.hnieacm.user.service.impl;

import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.Result;
import com.hnieacm.user.dto.InternalTransferSubmissionOwnerRequest;
import com.hnieacm.user.dto.TransferUserSubmissionsRequest;
import com.hnieacm.user.entity.UserInfo;
import com.hnieacm.user.feign.SubmissionInternalFeignClient;
import com.hnieacm.user.service.manager.UserInfoManager;
import com.hnieacm.user.vo.InternalTransferSubmissionOwnerVo;
import com.hnieacm.user.vo.TransferUserSubmissionsVo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 用户提交源码转移服务测试
 */
@ExtendWith(MockitoExtension.class)
class UserSubmissionTransferServiceImplTest {

    @Mock
    private UserInfoManager userInfoManager;

    @Mock
    private SubmissionInternalFeignClient submissionInternalFeignClient;

    @Test
    void shouldTransferSubmissionOwnerThroughInternalClient() {
        UserSubmissionTransferServiceImpl service =
                new UserSubmissionTransferServiceImpl(userInfoManager, submissionInternalFeignClient);
        UserInfo target = new UserInfo();
        target.setUid("target");
        target.setUsername("目标用户");
        when(userInfoManager.getUserByUid("source")).thenReturn(new UserInfo());
        when(userInfoManager.getUserByUid("target")).thenReturn(target);
        InternalTransferSubmissionOwnerVo internalVo = new InternalTransferSubmissionOwnerVo();
        internalVo.setTransferredCount(3);
        when(submissionInternalFeignClient.transferOwner(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Result.success(internalVo));

        TransferUserSubmissionsRequest request = new TransferUserSubmissionsRequest();
        request.setTargetUid(" target ");
        TransferUserSubmissionsVo result = service.transfer(" source ", request);

        ArgumentCaptor<InternalTransferSubmissionOwnerRequest> requestCaptor =
                ArgumentCaptor.forClass(InternalTransferSubmissionOwnerRequest.class);
        verify(submissionInternalFeignClient).transferOwner(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getSourceUid()).isEqualTo("source");
        assertThat(requestCaptor.getValue().getTargetUid()).isEqualTo("target");
        assertThat(requestCaptor.getValue().getTargetUsername()).isEqualTo("目标用户");
        assertThat(result.getTransferredCount()).isEqualTo(3);
    }

    @Test
    void shouldRejectDeletingOriginalRecords() {
        UserSubmissionTransferServiceImpl service =
                new UserSubmissionTransferServiceImpl(userInfoManager, submissionInternalFeignClient);
        TransferUserSubmissionsRequest request = new TransferUserSubmissionsRequest();
        request.setTargetUid("target");
        request.setDeleteOriginal(true);

        assertThatThrownBy(() -> service.transfer("source", request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不支持删除原记录");
    }
}
