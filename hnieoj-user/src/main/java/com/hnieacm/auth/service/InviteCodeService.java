package com.hnieacm.auth.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hnieacm.auth.entity.InviteCode;
import com.hnieacm.auth.mapper.InviteCodeMapper;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;

/**
 * @Author: HnieOJ contributors
 * @Description: Issue and atomically consume one-time registration invitations.
 */
@Service
@RequiredArgsConstructor
public class InviteCodeService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long MAX_VALIDITY_MILLIS = 30L * 24 * 60 * 60 * 1000;

    private final InviteCodeMapper inviteCodeMapper;

    public String create(long expiresAt, String operatorUid) {
        long now = System.currentTimeMillis();
        if (expiresAt <= now || expiresAt > now + MAX_VALIDITY_MILLIS) {
            throw new BizException(ResultCode.BAD_REQUEST, "邀请码有效期必须在未来 30 天内");
        }
        LocalDateTime expiry = LocalDateTime.ofInstant(Instant.ofEpochMilli(expiresAt), ZoneId.systemDefault());
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        InviteCode invitation = new InviteCode();
        invitation.setCodeHash(DigestUtil.sha256Hex(code));
        invitation.setStatus(1);
        invitation.setCreatedBy(operatorUid);
        invitation.setExpiresAt(expiry);
        inviteCodeMapper.insert(invitation);
        return code;
    }

    public List<InviteCode> list() {
        return inviteCodeMapper.selectList(new LambdaQueryWrapper<InviteCode>()
                .select(InviteCode::getId, InviteCode::getStatus, InviteCode::getCreatedBy,
                        InviteCode::getUsedUid, InviteCode::getUsedAt, InviteCode::getExpiresAt,
                        InviteCode::getGmtCreate)
                .orderByDesc(InviteCode::getId)
                .last("limit 100"));
    }

    public void revoke(long id) {
        int changed = inviteCodeMapper.update(null, new LambdaUpdateWrapper<InviteCode>()
                .eq(InviteCode::getId, id)
                .eq(InviteCode::getStatus, 1)
                .isNull(InviteCode::getUsedUid)
                .set(InviteCode::getStatus, 0));
        if (changed == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "邀请码不存在或已失效");
        }
    }

    public void consume(String code, String uid) {
        String normalized = StrUtil.trimToNull(code);
        if (normalized == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请输入邀请码");
        }
        int changed = inviteCodeMapper.update(null, new LambdaUpdateWrapper<InviteCode>()
                .eq(InviteCode::getCodeHash, DigestUtil.sha256Hex(normalized))
                .eq(InviteCode::getStatus, 1)
                .isNull(InviteCode::getUsedUid)
                .gt(InviteCode::getExpiresAt, LocalDateTime.now())
                .set(InviteCode::getUsedUid, uid)
                .set(InviteCode::getUsedAt, LocalDateTime.now()));
        if (changed != 1) {
            throw new BizException(ResultCode.BAD_REQUEST, "邀请码无效、已使用或已过期");
        }
    }
}
