package com.hnieacm.judge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 临时判题节点兑换 Token 请求
 */
@Data
public class ExchangeJudgeTempTokenRequest {

    @NotBlank(message = "authCode 不能为空")
    private String authCode;

    private String nodeName;

    @Valid
    @NotNull(message = "fingerprint 不能为空")
    private Fingerprint fingerprint;

    @Valid
    @NotNull(message = "proof 不能为空")
    private Proof proof;

    @Data
    public static class Fingerprint {

        @NotBlank(message = "instanceId 不能为空")
        private String instanceId;

        private String nodeName;

        @NotBlank(message = "hostnameHash 不能为空")
        private String hostnameHash;

        @NotBlank(message = "machineIdHash 不能为空")
        private String machineIdHash;

        private List<String> macAddressHashes;

        private List<String> ipAddressHashes;

        private List<String> supportedJudgeModes;

        private String clientTime;
    }

    @Data
    public static class Proof {

        @NotBlank(message = "proof.type 不能为空")
        private String type;

        @NotBlank(message = "proof.publicKey 不能为空")
        private String publicKey;
    }
}
