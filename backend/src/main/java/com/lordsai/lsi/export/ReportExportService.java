package com.lordsai.lsi.export;

import com.lordsai.lsi.automation.service.AcadReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Turns an {@link ExportTable} into a downloadable CSV / Excel / PDF response. One place for
 * the content types, file names and no-store caching of every export in the application.
 */
@Service
public class ReportExportService {

    public static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);

    private final ExcelExporter excelExporter;
    private final PdfRenderer pdfRenderer;

    public ReportExportService(ExcelExporter excelExporter, PdfRenderer pdfRenderer) {
        this.excelExporter = excelExporter;
        this.pdfRenderer = pdfRenderer;
    }

    public ResponseEntity<byte[]> respond(String format, String baseName, ExportTable table) {
        String fmt = format == null ? "csv" : format.toLowerCase(Locale.ROOT);
        String name = baseName + "-" + LocalDate.now(ZoneId.of("Asia/Kolkata"));
        return switch (fmt) {
            case "xlsx", "excel" -> download(excelExporter.xlsx(table), XLSX, name + ".xlsx");
            case "pdf" -> download(pdf(table), MediaType.APPLICATION_PDF, name + ".pdf");
            default -> download(AcadReportService.csv(table.toAcadTable()).getBytes(StandardCharsets.UTF_8),
                    new MediaType("text", "csv", StandardCharsets.UTF_8), name + ".csv");
        };
    }

    public byte[] pdf(ExportTable table) {
        return pdfRenderer.pdf("export/report", model(table));
    }

    /** Printer-friendly XHTML of the same table (browser "Print" view). */
    public String html(ExportTable table) {
        return pdfRenderer.html("export/report", model(table));
    }

    private Map<String, Object> model(ExportTable table) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", table.title());
        m.put("columns", table.columns());
        m.put("rows", table.rows());
        Map<String, String> filters = new LinkedHashMap<>();
        table.filters().forEach((k, v) -> {
            if (v != null && !v.isBlank()) {
                filters.put(k, v);
            }
        });
        m.put("filters", filters);
        m.put("totals", table.totals());
        m.put("recordCount", table.recordCount());
        m.put("generatedAt", STAMP.format(table.generatedAt().atZone(ZoneId.of("Asia/Kolkata"))) + " IST");
        return m;
    }

    private static ResponseEntity<byte[]> download(byte[] body, MediaType type, String filename) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename.replace("\"", "") + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }
}
