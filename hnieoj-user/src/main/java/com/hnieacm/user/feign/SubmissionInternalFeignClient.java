package com.hnieacm.user.feign;

import com.hnieacm.common.result.Result;
import com.hnieacm.user.dto.InternalTransferSubmissionOwnerRequest;
import com.hnieacm.user.vo.InternalTransferSubmissionOwnerVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 提交服务内部调用客户端
 */
@FeignClient(name = "hnieoj-submission")
public interface SubmissionInternalFeignClient {

    @PostMapping("/internal/submissions/owner-transfer")
    Result<InternalTransferSubmissionOwnerVo> transferOwner(@RequestBody InternalTransferSubmissionOwnerRequest request);
}
