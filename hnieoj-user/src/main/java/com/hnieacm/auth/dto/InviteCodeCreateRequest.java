package com.hnieacm.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @Author: HnieOJ contributors
 * @Description: Expiration time for a new one-time registration invitation.
 */
@Data
public class InviteCodeCreateRequest {

    @NotNull(message = "expiresAt 不能为空")
    private Long expiresAt;
}
