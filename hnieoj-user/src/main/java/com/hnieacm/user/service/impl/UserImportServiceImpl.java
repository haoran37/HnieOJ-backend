package com.hnieacm.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.common.util.SimpleExcelUtils;
import com.hnieacm.user.dto.CreateUserRequest;
import com.hnieacm.user.service.UserImportService;
import com.hnieacm.user.service.UserManageService;
import com.hnieacm.user.vo.CreateUserVo;
import com.hnieacm.user.vo.UserImportResultVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 用户导入服务实现
 */
@Service
@RequiredArgsConstructor
public class UserImportServiceImpl implements UserImportService {

    private static final int MAX_IMPORT_ROWS = 1000;

    private static final List<String> HEADERS = List.of(
            "uid", "username", "email", "password", "phone", "avatar", "collegeId", "classId", "grade"
    );

    private final UserManageService userManageService;

    @Override
    public byte[] buildTemplate() {
        return SimpleExcelUtils.buildTemplate("用户导入模板", HEADERS, List.of(List.of(
                "20220001", "张三", "zhangsan@example.com", "", "13800138000", "",
                "1", "1", "2022"
        )));
    }

    @Override
    public UserImportResultVo importUsers(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "Excel 文件不能为空");
        }
        List<Map<String, String>> rows;
        try {
            rows = SimpleExcelUtils.readRows(file.getInputStream(), HEADERS, MAX_IMPORT_ROWS);
        } catch (IOException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "Excel 文件读取失败");
        }

        UserImportResultVo result = new UserImportResultVo();
        for (Map<String, String> row : rows) {
            int rowNo = Integer.parseInt(row.get("_rowNo"));
            String uid = row.get("uid");
            try {
                CreateUserVo created = userManageService.createUser(toCreateRequest(row));
                result.addSuccess(uid, created == null ? null : created.getInitialPassword());
            } catch (Exception e) {
                result.addFailure(rowNo, uid, resolveErrorMessage(e));
            }
        }
        return result;
    }

    private CreateUserRequest toCreateRequest(Map<String, String> row) {
        CreateUserRequest request = new CreateUserRequest();
        request.setUid(require(row, "uid"));
        request.setUsername(require(row, "username"));
        request.setEmail(row.get("email"));
        request.setPassword(row.get("password"));
        request.setPhone(row.get("phone"));
        request.setAvatar(row.get("avatar"));
        request.setCollegeId(parseLong(row.get("collegeId"), "collegeId"));
        request.setClassId(parseLong(row.get("classId"), "classId"));
        request.setGrade(row.get("grade"));
        return request;
    }

    private String require(Map<String, String> row, String fieldName) {
        String value = StrUtil.trimToNull(row.get(fieldName));
        if (value == null) {
            throw new BizException(ResultCode.BAD_REQUEST, fieldName + " 不能为空");
        }
        return value;
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
