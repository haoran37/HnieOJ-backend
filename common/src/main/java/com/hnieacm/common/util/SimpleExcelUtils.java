package com.hnieacm.common.util;

import cn.hutool.core.util.StrUtil;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 简单 Excel 模板与导入工具
 */
public final class SimpleExcelUtils {

    private SimpleExcelUtils() {
    }

    public static List<Map<String, String>> readRows(InputStream inputStream, List<String> headers, int maxRows) {
        if (inputStream == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "Excel 文件不能为空");
        }
        if (headers == null || headers.isEmpty()) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "Excel 表头配置不能为空");
        }
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "Excel 文件没有工作表");
            }
            Map<String, Integer> headerIndex = resolveHeaderIndex(sheet.getRow(0), headers);
            DataFormatter formatter = new DataFormatter();
            List<Map<String, String>> rows = new ArrayList<>();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (isBlankRow(row, formatter)) {
                    continue;
                }
                if (rows.size() >= maxRows) {
                    throw new BizException(ResultCode.BAD_REQUEST, "单次最多导入 " + maxRows + " 行");
                }
                Map<String, String> values = new LinkedHashMap<>();
                values.put("_rowNo", String.valueOf(rowIndex + 1));
                for (String header : headers) {
                    values.put(header, readCell(row, headerIndex.get(header), formatter));
                }
                rows.add(values);
            }
            if (rows.isEmpty()) {
                throw new BizException(ResultCode.BAD_REQUEST, "Excel 文件没有可导入数据");
            }
            return rows;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.BAD_REQUEST, "Excel 文件解析失败");
        }
    }

    public static byte[] buildTemplate(String sheetName, List<String> headers, List<List<String>> examples) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(StrUtil.blankToDefault(sheetName, "template"));
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 18 * 256);
            }
            if (examples != null) {
                for (int rowIndex = 0; rowIndex < examples.size(); rowIndex++) {
                    Row row = sheet.createRow(rowIndex + 1);
                    List<String> example = examples.get(rowIndex);
                    for (int colIndex = 0; colIndex < example.size(); colIndex++) {
                        row.createCell(colIndex).setCellValue(example.get(colIndex));
                    }
                }
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "Excel 模板生成失败");
        }
    }

    private static Map<String, Integer> resolveHeaderIndex(Row headerRow, List<String> headers) {
        if (headerRow == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "Excel 第一行必须是表头");
        }
        DataFormatter formatter = new DataFormatter();
        Map<String, Integer> actualHeaders = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String value = StrUtil.trimToNull(formatter.formatCellValue(cell));
            if (value != null) {
                actualHeaders.put(value, cell.getColumnIndex());
            }
        }
        for (String header : headers) {
            if (!actualHeaders.containsKey(header)) {
                throw new BizException(ResultCode.BAD_REQUEST, "Excel 缺少表头：" + header);
            }
        }
        return actualHeaders;
    }

    private static boolean isBlankRow(Row row, DataFormatter formatter) {
        if (row == null) {
            return true;
        }
        for (Cell cell : row) {
            if (StrUtil.isNotBlank(formatter.formatCellValue(cell))) {
                return false;
            }
        }
        return true;
    }

    private static String readCell(Row row, Integer columnIndex, DataFormatter formatter) {
        if (row == null || columnIndex == null) {
            return null;
        }
        return StrUtil.trimToNull(formatter.formatCellValue(row.getCell(columnIndex)));
    }
}
