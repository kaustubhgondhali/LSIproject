package com.lordsai.lsi.export;

import com.lordsai.lsi.automation.service.AcadReportService;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One tabular dataset ready for Print / PDF / Excel. Carries the report title, the filters that
 * were applied (so every export says exactly what it contains), the generation time and the
 * record count alongside the columns and rows. Every export path — Automation Admin reports,
 * purchase reports, invoices, communication history — produces one of these.
 */
public record ExportTable(String title,
                          List<String> columns,
                          List<List<Object>> rows,
                          Map<String, String> filters,
                          Map<String, Object> totals,
                          Instant generatedAt) {

    public int recordCount() {
        return rows == null ? 0 : rows.size();
    }

    public static ExportTable of(String title, List<String> columns, List<List<Object>> rows,
                                 Map<String, String> filters, Map<String, Object> totals) {
        return new ExportTable(title, columns, rows,
                filters == null ? new LinkedHashMap<>() : filters,
                totals == null ? new LinkedHashMap<>() : totals, Instant.now());
    }

    /** Adapts the Automation Admin report tables without changing them. */
    public static ExportTable from(AcadReportService.Table table, Map<String, String> filters) {
        return of(table.title(), table.columns(), table.rows(), filters, table.totals());
    }

    public AcadReportService.Table toAcadTable() {
        return new AcadReportService.Table(title, columns, rows, totals);
    }
}
