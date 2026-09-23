package com.lordsai.lsi.export;

import com.lordsai.lsi.exception.ApiException;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Real .xlsx exports (Apache POI): numbers stay numbers, one column per field, header row
 * frozen and filterable, with the report title, applied filters, generation time and record
 * count above the table. Never a screenshot.
 */
@Component
public class ExcelExporter {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);
    private static final byte[] NAVY = {(byte) 0x0A, (byte) 0x11, (byte) 0x28};
    private static final byte[] BLUE = {(byte) 0x0B, (byte) 0x5F, (byte) 0xA5};

    public byte[] xlsx(ExportTable table) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(sheetName(table.title()));
            int cols = Math.max(1, table.columns().size());

            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleFont.setColor(IndexedColors.WHITE.getIndex());
            XSSFCellStyle titleStyle = wb.createCellStyle();
            titleStyle.setFont(titleFont);
            titleStyle.setFillForegroundColor(new XSSFColor(NAVY, null));
            titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            titleStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);

            Font metaFont = wb.createFont();
            metaFont.setFontHeightInPoints((short) 9);
            metaFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            CellStyle metaStyle = wb.createCellStyle();
            metaStyle.setFont(metaFont);

            Font headFont = wb.createFont();
            headFont.setBold(true);
            headFont.setColor(IndexedColors.WHITE.getIndex());
            XSSFCellStyle headStyle = wb.createCellStyle();
            headStyle.setFont(headFont);
            headStyle.setFillForegroundColor(new XSSFColor(BLUE, null));
            headStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle textStyle = wb.createCellStyle();
            textStyle.setBorderBottom(BorderStyle.HAIR);
            CellStyle numberStyle = wb.createCellStyle();
            numberStyle.setBorderBottom(BorderStyle.HAIR);
            numberStyle.setAlignment(HorizontalAlignment.RIGHT);
            numberStyle.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
            CellStyle intStyle = wb.createCellStyle();
            intStyle.setBorderBottom(BorderStyle.HAIR);
            intStyle.setAlignment(HorizontalAlignment.RIGHT);
            intStyle.setDataFormat(wb.createDataFormat().getFormat("0"));

            int r = 0;
            Row titleRow = sheet.createRow(r++);
            titleRow.setHeightInPoints(26);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Lord Sai Share Market Classes — " + table.title());
            titleCell.setCellStyle(titleStyle);
            for (int c = 1; c < cols; c++) {
                titleRow.createCell(c).setCellStyle(titleStyle);
            }
            if (cols > 1) {
                sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, cols - 1));
            }

            String stamp = STAMP.format(table.generatedAt().atZone(ZoneId.of("Asia/Kolkata"))) + " IST";
            meta(sheet, r++, "Generated: " + stamp + "   ·   Records: " + table.recordCount(), metaStyle);
            String filters = filtersLine(table.filters());
            meta(sheet, r++, "Filters: " + (filters.isEmpty() ? "none" : filters), metaStyle);
            r++;

            int headerRowIndex = r;
            Row head = sheet.createRow(r++);
            for (int c = 0; c < table.columns().size(); c++) {
                Cell cell = head.createCell(c);
                cell.setCellValue(table.columns().get(c));
                cell.setCellStyle(headStyle);
            }
            for (List<Object> row : table.rows()) {
                Row xr = sheet.createRow(r++);
                for (int c = 0; c < row.size(); c++) {
                    Cell cell = xr.createCell(c);
                    Object v = row.get(c);
                    if (v instanceof Number n) {
                        if (v instanceof Integer || v instanceof Long || v instanceof Short) {
                            cell.setCellValue(n.longValue());
                            cell.setCellStyle(intStyle);
                        } else {
                            cell.setCellValue(v instanceof BigDecimal bd ? bd.doubleValue() : n.doubleValue());
                            cell.setCellStyle(numberStyle);
                        }
                    } else if (v instanceof Boolean b) {
                        cell.setCellValue(b ? "Yes" : "No");
                        cell.setCellStyle(textStyle);
                    } else {
                        cell.setCellValue(safeText(v == null ? "" : String.valueOf(v)));
                        cell.setCellStyle(textStyle);
                    }
                }
            }
            if (!table.totals().isEmpty()) {
                r++;
                Row tr = sheet.createRow(r);
                Cell tc = tr.createCell(0);
                tc.setCellValue("Totals: " + totalsLine(table.totals()));
                tc.setCellStyle(metaStyle);
            }

            sheet.createFreezePane(0, headerRowIndex + 1);
            if (!table.rows().isEmpty() && cols > 0) {
                sheet.setAutoFilter(new CellRangeAddress(headerRowIndex, headerRowIndex + table.rows().size(), 0, cols - 1));
            }
            for (int c = 0; c < cols; c++) {
                sheet.autoSizeColumn(c);
                int width = sheet.getColumnWidth(c);
                sheet.setColumnWidth(c, Math.min(Math.max(width + 512, 3000), 14000));
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "The Excel file could not be generated.");
        }
    }

    private static void meta(Sheet sheet, int rowIndex, String text, CellStyle style) {
        Row row = sheet.createRow(rowIndex);
        Cell cell = row.createCell(0);
        cell.setCellValue(text);
        cell.setCellStyle(style);
    }

    static String filtersLine(Map<String, String> filters) {
        StringBuilder sb = new StringBuilder();
        filters.forEach((k, v) -> {
            if (v != null && !v.isBlank()) {
                if (sb.length() > 0) {
                    sb.append("  ·  ");
                }
                sb.append(k).append(": ").append(v);
            }
        });
        return sb.toString();
    }

    static String totalsLine(Map<String, Object> totals) {
        StringBuilder sb = new StringBuilder();
        totals.forEach((k, v) -> {
            if (sb.length() > 0) {
                sb.append("  ·  ");
            }
            sb.append(k).append(": ").append(v);
        });
        return sb.toString();
    }

    /** Neutralises spreadsheet formula injection for text cells. */
    static String safeText(String s) {
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0 && !s.matches("^-?\\d+(\\.\\d+)?$")) {
            return "'" + s;
        }
        return s;
    }

    private static String sheetName(String title) {
        String name = title == null ? "Report" : title.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        return name.isEmpty() ? "Report" : name.substring(0, Math.min(31, name.length()));
    }
}
