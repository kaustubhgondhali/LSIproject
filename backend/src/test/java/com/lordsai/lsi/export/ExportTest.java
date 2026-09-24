package com.lordsai.lsi.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.lordsai.lsi.entity.enums.AccountStatus;
import com.lordsai.lsi.entity.enums.Role;
import com.lordsai.lsi.support.ApiClient;
import com.lordsai.lsi.support.TestUsers;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Automation Admin Print / PDF / Excel: every record report is exportable in all three forms and
 * the applied filters (search, status, batch) travel into the export.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ExportTest {

    @Autowired ApiClient api;
    @Autowired TestUsers users;
    @Autowired ExcelExporter excelExporter;

    private String office;
    private long batchId;

    @BeforeEach
    void setUp() throws Exception {
        users.create("office@test.local", Role.AUTOMATION_ADMIN, AccountStatus.ACTIVE);
        office = api.login("office@test.local", TestUsers.PASSWORD);
        JsonNode lookups = api.data(api.get(office, "/api/automation/lookups"));
        batchId = lookups.path("batches").get(0).path("id").asLong();
        long courseId = lookups.path("courses").get(0).path("id").asLong();
        api.post(office, "/api/automation/students", Map.of("fullName", "Export Alpha", "mobile", "9000000001", "batchId", batchId,
                "courseId", courseId, "courseFee", 10000, "admissionDate", "2026-01-10")).andExpect(status().isOk());
        api.post(office, "/api/automation/students", Map.of("fullName", "Export Beta", "mobile", "9000000002", "batchId", batchId,
                "courseId", courseId, "courseFee", 10000, "admissionDate", "2026-01-11")).andExpect(status().isOk());
    }

    @Test
    void everyReportExportsAsCsvXlsxPdfAndPrint() throws Exception {
        for (String report : List.of("students", "fee-collection", "receipts", "attendance", "batches", "courses-master",
                "payment-modes", "purchases", "invoices", "communications", "ebooks")) {
            api.get(office, "/api/automation/reports/" + report).andExpect(status().isOk());
            api.get(office, "/api/automation/reports/" + report + "/csv").andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv")));
            api.get(office, "/api/automation/reports/" + report + "/xlsx").andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("application/vnd.openxmlformats")));
            api.get(office, "/api/automation/reports/" + report + "/pdf").andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/pdf"));
            api.get(office, "/api/automation/reports/" + report + "/print").andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/html")));
        }
        api.get(office, "/api/automation/reports/does-not-exist/xlsx").andExpect(status().isBadRequest());
    }

    @Test
    void filteredExportContainsOnlyMatchingRowsAndTheAppliedFilters() throws Exception {
        JsonNode all = api.data(api.get(office, "/api/automation/reports/students?batchId=" + batchId));
        assertThat(all.path("rows").size()).isEqualTo(2);
        JsonNode filtered = api.data(api.get(office, "/api/automation/reports/students?batchId=" + batchId + "&q=beta"));
        assertThat(filtered.path("rows").size()).isEqualTo(1);
        assertThat(filtered.path("rows").get(0).get(1).asText()).isEqualTo("Export Beta");

        byte[] xlsx = api.get(office, "/api/automation/reports/students/xlsx?batchId=" + batchId + "&q=beta&paymentStatus=PENDING")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).contains("Student Report");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).contains("Records: 1");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).contains("Search: beta").contains("Payment status: PENDING");
            assertThat(sheet.getRow(4).getCell(0).getStringCellValue()).isEqualTo("Student ID");
            assertThat(sheet.getRow(5).getCell(1).getStringCellValue()).isEqualTo("Export Beta");
            // Numbers are real numeric cells, not text.
            assertThat(sheet.getRow(5).getCell(7).getNumericCellValue()).isEqualTo(10000.0);
            assertThat(sheet.getRow(6)).isNull();
        }

        String print = api.get(office, "/api/automation/reports/students/print?batchId=" + batchId + "&q=beta")
                .andReturn().getResponse().getContentAsString();
        assertThat(print).contains("Student Report", "Applied filters", "Search", "beta", "1 record(s)", "Export Beta").doesNotContain("Export Alpha");
        String csv = api.get(office, "/api/automation/reports/students/csv?batchId=" + batchId + "&q=beta")
                .andReturn().getResponse().getContentAsString();
        assertThat(csv).contains("Export Beta").doesNotContain("Export Alpha");
    }

    @Test
    void excelCellsNeutraliseFormulaInjection() throws Exception {
        ExportTable t = ExportTable.of("Injection", List.of("Name", "Amount"), List.of(List.of("=HYPERLINK(\"http://evil\")", 12.5)),
                Map.of("Search", "x"), Map.of());
        byte[] xlsx = excelExporter.xlsx(t);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(wb.getSheetAt(0).getRow(5).getCell(0).getStringCellValue()).startsWith("'=HYPERLINK");
            assertThat(wb.getSheetAt(0).getRow(5).getCell(1).getNumericCellValue()).isEqualTo(12.5);
        }
    }
}
