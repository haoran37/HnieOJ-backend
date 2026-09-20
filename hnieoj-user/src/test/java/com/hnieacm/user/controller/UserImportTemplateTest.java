package com.hnieacm.user.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.hnieacm.common.constant.PermissionConstant;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Author: HaoRan_Lyu
 * @Description: 用户导入模板接口回归：GET /api/users/import/template 保持 USER_MANAGE 管理权限，
 * 返回完整可解压 OOXML，首行包含 CreateUserRequest 的 7 个字段标题。
 */
class UserImportTemplateTest {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    @Test
    void endpointIsDeclaredAsGetUnderUserManageAndKeepsUserManagePermission() throws Exception {
        RequestMapping base = UserManageController.class.getAnnotation(RequestMapping.class);
        assertThat(base).isNotNull();
        assertThat(base.value()).containsExactly("/api/users");

        SaCheckPermission permission = UserManageController.class.getAnnotation(SaCheckPermission.class);
        assertThat(permission).isNotNull();
        assertThat(permission.value()).containsExactly(PermissionConstant.USER_MANAGE);

        Method method = UserManageController.class.getMethod("downloadImportTemplate");
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/import/template");
    }

    @Test
    void templateIsAFullOoxmlWorkbookWithSevenExpectedHeaders() throws Exception {
        ResponseEntity<Resource> response = new UserManageController(null).downloadImportTemplate();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(XLSX);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment")
                .contains("user-import-template.xlsx");
        assertThat(response.getBody()).isNotNull();

        Map<String, byte[]> parts = readZip(response.getBody().getInputStream());
        assertThat(parts).containsKeys(
                "[Content_Types].xml",
                "_rels/.rels",
                "xl/workbook.xml",
                "xl/worksheets/sheet1.xml",
                "xl/sharedStrings.xml");

        assertThat(readFirstRowHeaders(parts))
                .containsExactly("uid", "username", "password", "email", "collegeId", "classId", "grade");
    }

    private static Map<String, byte[]> readZip(InputStream input) throws Exception {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                parts.put(entry.getName(), zip.readAllBytes());
            }
        }
        return parts;
    }

    private static List<String> readFirstRowHeaders(Map<String, byte[]> parts) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);

        Document shared = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(parts.get("xl/sharedStrings.xml")));
        NodeList items = shared.getElementsByTagNameNS("*", "si");
        List<String> sharedStrings = new ArrayList<>();
        for (int i = 0; i < items.getLength(); i++) {
            sharedStrings.add(items.item(i).getTextContent());
        }

        Document sheet = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(parts.get("xl/worksheets/sheet1.xml")));
        NodeList cells = sheet.getElementsByTagNameNS("*", "c");
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < cells.getLength(); i++) {
            Element cell = (Element) cells.item(i);
            assertThat(cell.getAttribute("t")).isEqualTo("s");
            Node value = cell.getElementsByTagNameNS("*", "v").item(0);
            headers.add(sharedStrings.get(Integer.parseInt(value.getTextContent())));
        }
        return headers;
    }
}
