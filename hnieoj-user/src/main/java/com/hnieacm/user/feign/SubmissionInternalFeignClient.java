package com.hnieacm.user.feign;

import com.hnieacm.common.result.Result;
import com.hnieacm.user.dto.InternalTransferSubmissionOwnerRequest;
import com.hnieacm.user.vo.InternalTransferSubmissionOwnerVo;
import com.hnieacm.user.vo.RegisterEmailCheckVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 提交服务内部调用客户端
 */
@FeignClient(name = "hnieoj-submission")
public interface SubmissionInternalFeignClient {

    /**
     * Check whether an email may submit a public registration application.
     *
     * @param email registration email
     * @return registration policy result
     */
    @GetMapping("/api/system/register/check-email")
    Result<RegisterEmailCheckVo> checkRegisterEmail(@RequestParam("email") String email);

    @PostMapping("/internal/submissions/owner-transfer")
    Result<InternalTransferSubmissionOwnerVo> transferOwner(@RequestBody InternalTransferSubmissionOwnerRequest request);
}
