package com.hnieacm.auth.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hnieacm.auth.dto.RegisterRequest;
import com.hnieacm.auth.service.AuthService;
import com.hnieacm.auth.service.RegistrationImportService;
import com.hnieacm.auth.service.RegistrationReviewService;
import com.hnieacm.auth.vo.RegistrationImportResultVo;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.common.util.SimpleExcelUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 注册信息导入服务实现
 */
@Service
@RequiredArgsConstructor
public class RegistrationImportServiceImpl implements RegistrationImportService {

    private static final int MAX_IMPORT_ROWS = 1000;

    private static final List<String> HEADERS = List.of(
            "uid", "username", "password", "email", "collegeId", "classId", "grade", "qq"
    );

    private final AuthService authService;
    private final RegistrationReviewService registrationReviewService;

    @Override
    public RegistrationImportResultVo importAndApprove(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "Excel 文件不能为空");
        }
        List<Map<String, String>> rows;
        try {
            rows = SimpleExcelUtils.readRows(file.getInputStream(), HEADERS, MAX_IMPORT_ROWS);
        } catch (IOException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "Excel 文件读取失败");
        }

        RegistrationImportResultVo result = new RegistrationImportResultVo();
        for (Map<String, String> row : rows) {
            int rowNo = Integer.parseInt(row.get("_rowNo"));
            String uid = row.get("uid");
            try {
                authService.register(toRegisterRequest(row));
                registrationReviewService.approve(uid);
                result.addSuccess(uid);
            } catch (Exception e) {
                result.addFailure(rowNo, uid, resolveErrorMessage(e));
            }
        }
        return result;
    }

    private RegisterRequest toRegisterRequest(Map<String, String> row) {
        RegisterRequest request = new RegisterRequest();
        request.setUid(require(row, "uid"));
        request.setUsername(require(row, "username"));
        request.setPassword(require(row, "password"));
        request.setEmail(require(row, "email"));
        request.setCollegeId(requireLong(row, "collegeId"));
        request.setClassId(requireLong(row, "classId"));
        request.setGrade(require(row, "grade"));
        request.setQq(require(row, "qq"));
        return request;
    }

    private String require(Map<String, String> row, String fieldName) {
        String value = StrUtil.trimToNull(row.get(fieldName));
        if (value == null) {
            throw new BizException(ResultCode.BAD_REQUEST, fieldName + " 不能为空");
        }
        return value;
    }

    private Long requireLong(Map<String, String> row, String fieldName) {
        return parseLong(require(row, fieldName), fieldName);
    }

    private Long parseLong(String value, String fieldName) {
        String normalized = StrUtil.trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException e) {
            throw new BizException(ResultCode.BAD_REQUEST, fieldName + " 必须是数字");
        }
    }

    private String resolveErrorMessage(Exception e) {
        if (e instanceof BizException bizException) {
            return bizException.getMsg();
        }
        return StrUtil.blankToDefault(e.getMessage(), "导入失败");
    }
}
