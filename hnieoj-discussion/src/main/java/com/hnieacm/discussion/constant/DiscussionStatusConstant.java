package com.hnieacm.discussion.constant;

import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/22
 * @Description: 讨论状态常量
 */
public final class DiscussionStatusConstant {

    public static final int NORMAL = 0;
    public static final int CLOSED = 1;

    private DiscussionStatusConstant() {
    }

    public static int normalize(Integer status) {
        if (status == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "status 不能为空，仅支持 0/1");
        }
        if (status == NORMAL || status == CLOSED) {
            return status;
        }
        throw new BizException(ResultCode.BAD_REQUEST, "status 参数不合法，仅支持 0(正常)/1(关闭)");
    }
}
