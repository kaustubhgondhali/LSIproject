package com.lordsai.lsi.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.lordsai.lsi.automation.service.AmountInWords;
import com.lordsai.lsi.automation.service.AcadSequenceService;
import com.lordsai.lsi.support.ApiClient;
import com.lordsai.lsi.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Automation Admin: login/role, "enter once, reuse everywhere" workflows, and the workbook import rules. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AutomationAdminTest {

    @Autowired ApiClient api;
    @Autowired TestUsers users;

    private String auto;
    private long batch1, course1;

    @BeforeEach
    void setUp() throws Exception {
        auto = api.login("automation", "Automation@123");
        JsonNode l = api.data(api.get(auto, "/api/automation/lookups"));
        batch1 = l.path("batches").get(0).path("id").asLong();
        course1 = l.path("courses").get(0).path("id").asLong();
    }

    private Map<String, Object> student(String name, String mobile) {
        Map<String, Object> m = new HashMap<>();
        m.put("fullName", name); m.put("batchId", batch1); m.put("courseId", course1);
        m.put("admissionDate", "2026-09-01"); m.put("mobile", mobile);
        return m;
    }

    // ---- login / roles ---------------------------------------------------------------------------

    @Test
    void automationAdminLogsInThroughItsOwnPortalAndOnlyItsRolesReachTheApi() throws Exception {
        // A fresh login supersedes the setUp session (one live session per account), so use its token below.
        auto = api.data(api.post(null, "/api/auth/login", Map.of("identifier", "automation", "password", "Automation@123", "portal", "AUTOMATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value("AUTOMATION_ADMIN"))
                .andExpect(jsonPath("$.data.redirectUrl").value("automation-admin.html"))).path("accessToken").asText();
        // Wrong portal for this account, and the automation portal refuses students.
        api.post(null, "/api/auth/login", Map.of("identifier", "automation", "password", "Automation@123", "portal", "STUDENT"))
                .andExpect(status().isForbidden());
        users.student("pupil@test.local");
        api.post(null, "/api/auth/login", Map.of("identifier", "pupil@test.local", "password", TestUsers.PASSWORD, "portal", "AUTOMATION"))
                .andExpect(status().isForbidden());

        String student = api.login("pupil@test.local", TestUsers.PASSWORD);
        api.get(student, "/api/automation/dashboard").andExpect(status().isForbidden());
        api.get(null, "/api/automation/dashboard").andExpect(status().isUnauthorized());
        api.get(auto, "/api/automation/dashboard").andExpect(status().isOk());
        // The office role never reaches the Main Admin API.
        api.get(auto, "/api/admin/dashboard").andExpect(status().isForbidden());

        users.admin("boss@test.local");
        api.get(api.login("boss@test.local", TestUsers.PASSWORD), "/api/automation/dashboard").andExpect(status().isOk());
    }

    // ---- students -----------------------------------------------------------------------------

    @Test
    void studentIdsAreGeneratedSequentiallyAndTheFeeComesFromTheCourse() throws Exception {
        String first = api.data(api.post(auto, "/api/automation/students", student("Rahul Patil", "9876543210"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.courseFee").value(10000.0))
                .andExpect(jsonPath("$.data.paymentStatus").value("PENDING"))).path("studentId").asText();
        String second = api.data(api.post(auto, "/api/automation/students", student("Sneha More", "9876543211"))).path("studentId").asText();
        assertThat(first).matches("LSA/\\d{4}/0001");
        assertThat(second).matches("LSA/\\d{4}/0002");
        api.get(auto, "/api/automation/lookups").andExpect(jsonPath("$.data.nextStudentId").value(org.hamcrest.Matchers.endsWith("/0003")));

        // Global search finds by name, ID fragment and mobile.
        api.get(auto, "/api/automation/search?q=rahul").andExpect(jsonPath("$.data[0].studentId").value(first));
        api.get(auto, "/api/automation/search?q=0002").andExpect(jsonPath("$.data[0].fullName").value("Sneha More"));
        api.get(auto, "/api/automation/search?q=9876543211").andExpect(jsonPath("$.data[0].fullName").value("Sneha More"));

        Map<String, Object> bad = student("X", "12345");
        api.post(auto, "/api/automation/students", bad).andExpect(status().isBadRequest());
    }

    // ---- payments -> receipts ----------------------------------------------------------------------

    @Test
    void recordingAPaymentRecalculatesTotalsAndIssuesAReceiptAutomatically() throws Exception {
        long id = api.data(api.post(auto, "/api/automation/students", student("Pooja Thakur", "9876543212"))).path("id").asLong();

        JsonNode p1 = api.data(api.post(auto, "/api/automation/payments", Map.of("studentId", id, "installmentNo", 1, "paymentDate", "2026-09-05",
                        "amount", 4000, "paymentMode", "UPI", "referenceNo", "3265489721"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentNo").value(org.hamcrest.Matchers.startsWith("PAY/")))
                .andExpect(jsonPath("$.data.receiptNo").value(org.hamcrest.Matchers.startsWith("LSR/2026/"))));
        long receiptId = p1.path("receiptId").asLong();

        api.get(auto, "/api/automation/students/" + id + "/fees")
                .andExpect(jsonPath("$.data.totalPaid").value(4000.0))
                .andExpect(jsonPath("$.data.balance").value(6000.0))
                .andExpect(jsonPath("$.data.paymentStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.data.nextInstallmentNo").value(2))
                .andExpect(jsonPath("$.data.installments[0].paid").value(true));

        api.get(auto, "/api/automation/receipts/" + receiptId)
                .andExpect(jsonPath("$.data.studentName").value("Pooja Thakur"))
                .andExpect(jsonPath("$.data.totalFee").value(10000.0))
                .andExpect(jsonPath("$.data.amountPaid").value(4000.0))
                .andExpect(jsonPath("$.data.balance").value(6000.0))
                .andExpect(jsonPath("$.data.paymentMode").value("UPI"))
                .andExpect(jsonPath("$.data.amountInWords").value("Rupees Four Thousand Only"))
                .andExpect(jsonPath("$.data.academyName").value(org.hamcrest.Matchers.containsString("LORD SAI")));

        // Business rules: no duplicate installment, no overpayment, invalid mode.
        api.post(auto, "/api/automation/payments", Map.of("studentId", id, "installmentNo", 1, "paymentDate", "2026-09-06", "amount", 1000, "paymentMode", "CASH"))
                .andExpect(status().isConflict());
        api.post(auto, "/api/automation/payments", Map.of("studentId", id, "installmentNo", 2, "paymentDate", "2026-09-06", "amount", 6001, "paymentMode", "CASH"))
                .andExpect(status().isBadRequest());
        api.post(auto, "/api/automation/payments", Map.of("studentId", id, "installmentNo", 2, "paymentDate", "2026-09-06", "amount", 6000, "paymentMode", "BITCOIN"))
                .andExpect(status().isBadRequest());

        api.post(auto, "/api/automation/payments", Map.of("studentId", id, "installmentNo", 2, "paymentDate", "2026-09-06", "amount", 6000, "paymentMode", "CASH"))
                .andExpect(status().isOk());
        api.get(auto, "/api/automation/students/" + id).andExpect(jsonPath("$.data.paymentStatus").value("PAID")).andExpect(jsonPath("$.data.balance").value(0.0));

        JsonNode dash = api.data(api.get(auto, "/api/automation/dashboard"));
        assertThat(dash.path("paidStudents").asLong()).isEqualTo(1);
        assertThat(dash.path("totalPaid").decimalValue()).isEqualByComparingTo("10000");
        assertThat(dash.path("receiptsIssued").asLong()).isEqualTo(2);

        JsonNode profile = api.data(api.get(auto, "/api/automation/students/" + id + "/profile"));
        assertThat(profile.path("payments")).hasSize(2);
        assertThat(profile.path("receipts")).hasSize(2);
        assertThat(profile.path("activity").toString()).contains("ACAD_PAYMENT_RECORDED", "ACAD_RECEIPT_GENERATED");
    }

    // ---- permanent fee receipt number ----------------------------------------------------------

    @Test
    void everyInstallmentOfTheSameStudentSharesOnePermanentReceiptNumberWhileEachPaymentStaysUnique() throws Exception {
        long id = api.data(api.post(auto, "/api/automation/students", student("Rahul Sharma", "9876543215"))).path("id").asLong();

        // No receipt number yet: it is only assigned once the first payment is recorded.
        api.get(auto, "/api/automation/students/" + id + "/fees")
                .andExpect(jsonPath("$.data.feeReceiptNo").doesNotExist())
                .andExpect(jsonPath("$.data.installmentsRecorded").value(0));

        JsonNode p1 = api.data(api.post(auto, "/api/automation/payments", Map.of("studentId", id, "installmentNo", 1,
                "paymentDate", "2026-09-05", "amount", 3000, "paymentMode", "CASH")).andExpect(status().isOk()));
        String receiptNo = p1.path("receiptNo").asText();
        String paymentNo1 = p1.path("paymentNo").asText();
        assertThat(receiptNo).startsWith("LSR/");

        JsonNode p2 = api.data(api.post(auto, "/api/automation/payments", Map.of("studentId", id, "installmentNo", 2,
                "paymentDate", "2026-09-10", "amount", 3000, "paymentMode", "CASH")).andExpect(status().isOk()));
        JsonNode p3 = api.data(api.post(auto, "/api/automation/payments", Map.of("studentId", id, "installmentNo", 3,
                "paymentDate", "2026-09-15", "amount", 4000, "paymentMode", "CASH")).andExpect(status().isOk()));

        // Same overall Receipt No. on every installment...
        assertThat(p2.path("receiptNo").asText()).isEqualTo(receiptNo);
        assertThat(p3.path("receiptNo").asText()).isEqualTo(receiptNo);
        // ...but each payment keeps its own unique payment/transaction number.
        assertThat(p2.path("paymentNo").asText()).isNotEqualTo(paymentNo1).isNotEqualTo(p3.path("paymentNo").asText());
        assertThat(java.util.Set.of(paymentNo1, p2.path("paymentNo").asText(), p3.path("paymentNo").asText())).hasSize(3);

        // The fee summary now carries the permanent number and the count of recorded payments,
        // and every installment slot links to its own receipt id even though the number repeats.
        api.get(auto, "/api/automation/students/" + id + "/fees")
                .andExpect(jsonPath("$.data.feeReceiptNo").value(receiptNo))
                .andExpect(jsonPath("$.data.installmentsRecorded").value(3))
                .andExpect(jsonPath("$.data.installments[0].receiptNo").value(receiptNo))
                .andExpect(jsonPath("$.data.installments[1].receiptNo").value(receiptNo))
                .andExpect(jsonPath("$.data.installments[2].receiptNo").value(receiptNo))
                .andExpect(jsonPath("$.data.installments[0].receiptId").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.equalTo(p2.path("receiptId").asInt()))));

        // A second, unrelated student gets their OWN distinct permanent number, not this one.
        long other = api.data(api.post(auto, "/api/automation/students", student("Sneha Kulkarni", "9876543216"))).path("id").asLong();
        JsonNode op1 = api.data(api.post(auto, "/api/automation/payments", Map.of("studentId", other, "installmentNo", 1,
                "paymentDate", "2026-09-05", "amount", 2000, "paymentMode", "CASH")).andExpect(status().isOk()));
        assertThat(op1.path("receiptNo").asText()).isNotEqualTo(receiptNo);

        // Every receipt for the first student prints the same Receipt No., each with its own Payment No.
        JsonNode receipts = api.data(api.get(auto, "/api/automation/receipts?studentId=" + id));
        java.util.List<String> receiptNos = new java.util.ArrayList<>();
        java.util.Set<String> paymentNos = new java.util.HashSet<>();
        receipts.path("content").forEach(r -> { receiptNos.add(r.path("receiptNo").asText()); paymentNos.add(r.path("paymentNo").asText()); });
        assertThat(receiptNos).hasSize(3).containsOnly(receiptNo);
        assertThat(paymentNos).hasSize(3);
    }

    // ---- Automation Admin Settings: Student ID series ----------------------------------------

    @Test
    void studentIdSeriesCanBeMovedForwardByTheAdminWithoutReusingAnExistingId() throws Exception {
        api.post(auto, "/api/automation/students", student("Series One", "9876543217"));
        api.post(auto, "/api/automation/students", student("Series Two", "9876543218"));
        // Two students exist -> highest existing number is 2, next would normally be 3.
        JsonNode before = api.data(api.get(auto, "/api/automation/settings/student-id-sequence")
                .andExpect(jsonPath("$.data.highestExistingNumber").value(2))
                .andExpect(jsonPath("$.data.nextNumber").value(3)));
        int year = before.path("year").asInt();

        // Cannot reuse/collide with an existing ID.
        api.put(auto, "/api/automation/settings/student-id-sequence", Map.of("nextNumber", 2)).andExpect(status().isConflict());
        api.put(auto, "/api/automation/settings/student-id-sequence", Map.of("nextNumber", 1)).andExpect(status().isConflict());
        api.put(auto, "/api/automation/settings/student-id-sequence", Map.of("nextNumber", 0)).andExpect(status().isBadRequest());
        api.put(auto, "/api/automation/settings/student-id-sequence", Map.of("nextNumber", -5)).andExpect(status().isBadRequest());

        // A safe forward move is accepted and reflected immediately.
        api.put(auto, "/api/automation/settings/student-id-sequence", Map.of("nextNumber", 1001))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextNumber").value(1001))
                .andExpect(jsonPath("$.data.nextStudentId").value("LSA/" + year + "/1001"));
        api.get(auto, "/api/automation/settings/student-id-sequence").andExpect(jsonPath("$.data.nextNumber").value(1001));

        // The next student actually created gets exactly that ID...
        String third = api.data(api.post(auto, "/api/automation/students", student("Series Three", "9876543219"))).path("studentId").asText();
        assertThat(third).isEqualTo("LSA/" + year + "/1001");

        // ...and existing students keep their original IDs, untouched.
        api.get(auto, "/api/automation/search?q=Series One").andExpect(jsonPath("$.data[0].studentId").value("LSA/" + year + "/0001"));
        api.get(auto, "/api/automation/search?q=Series Two").andExpect(jsonPath("$.data[0].studentId").value("LSA/" + year + "/0002"));

        // Student-only / unauthenticated cannot touch it (the shared /api/automation/** rule).
        api.get(null, "/api/automation/settings/student-id-sequence").andExpect(status().isUnauthorized());
    }

    // ---- attendance ---------------------------------------------------------------------------------

    @Test
    void attendanceSheetLoadsTheBatchAndComputesPercentages() throws Exception {
        long a = api.data(api.post(auto, "/api/automation/students", student("Amit", "9876543213"))).path("id").asLong();
        long b = api.data(api.post(auto, "/api/automation/students", student("Neha", "9876543214"))).path("id").asLong();

        JsonNode sheet = api.data(api.post(auto, "/api/automation/attendance/sessions", Map.of("batchId", batch1, "sessionDate", "2026-09-06"))
                .andExpect(status().isOk()));
        long sessionId = sheet.path("session").path("id").asLong();
        assertThat(sheet.path("rows")).hasSize(2);
        // Re-opening the same batch+date returns the same session (no duplicates).
        assertThat(api.data(api.post(auto, "/api/automation/attendance/sessions", Map.of("batchId", batch1, "sessionDate", "2026-09-06"))).path("session").path("id").asLong()).isEqualTo(sessionId);

        api.post(auto, "/api/automation/attendance/save", Map.of("sessionId", sessionId, "marks", List.of(
                        Map.of("studentId", a, "status", "PRESENT"), Map.of("studentId", b, "status", "ABSENT"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.session.present").value(1)).andExpect(jsonPath("$.data.session.absent").value(1));
        api.post(auto, "/api/automation/attendance/sessions", Map.of("batchId", batch1, "sessionDate", "2026-09-13"));
        long s2 = api.data(api.get(auto, "/api/automation/attendance/sessions?batchId=" + batch1)).get(0).path("id").asLong();
        api.post(auto, "/api/automation/attendance/save", Map.of("sessionId", s2, "marks", List.of(
                Map.of("studentId", a, "status", "PRESENT"), Map.of("studentId", b, "status", "PRESENT")))).andExpect(status().isOk());

        api.get(auto, "/api/automation/students/" + b + "/profile")
                .andExpect(jsonPath("$.data.attendance.sessionsTotal").value(2))
                .andExpect(jsonPath("$.data.attendance.present").value(1))
                .andExpect(jsonPath("$.data.attendance.percent").value(50.0));
        api.get(auto, "/api/automation/students/" + a).andExpect(jsonPath("$.data.attendancePercent").value(100.0));
        api.get(auto, "/api/automation/reports/low-attendance?threshold=75").andExpect(jsonPath("$.data.rows.length()").value(1));
    }

    // ---- import -------------------------------------------------------------------------------------

    @Test
    void importNormalisesIdsSkipsPlaceholdersAndIsIdempotent() throws Exception {
        Map<String, Object> payload = Map.of(
                "students", List.of(
                        mapOf("studentId", "LSA/2026/0001", "fullName", "JOSTNA MHATRE", "batch", "1ST", "course", "Share Market Basic to Advance", "courseFee", 10000, "admissionDate", "2026-01-04", "mobile", "9168917681"),
                        mapOf("studentId", "LSA/2026/0015", "fullName", "SANGITA VIKAS MHATRE", "batch", "3RD", "course", "Share Market Basic to Advance", "courseFee", 10000, "admissionDateRaw", "12-07-20226", "mobile", "9545188851", "reviewNote", "admission date '12-07-20226' could not be read"),
                        mapOf("studentId", "LSA/2026/0021", "fullName", "", "batch", "3RD")),
                "payments", List.of(
                        mapOf("studentId", "LSA2026/0001", "installmentNo", 1, "paymentDate", "2026-12-28", "amount", 3000, "paymentMode", "OTHER", "reviewNote", "future date"),
                        mapOf("studentId", "LSA/2026/0001", "installmentNo", 2, "paymentDate", null, "amount", 3000, "paymentMode", "CASH", "reviewNote", "no date"),
                        mapOf("studentId", "LSA/2026/0099", "installmentNo", 1, "paymentDate", "2026-01-01", "amount", 100, "paymentMode", "CASH")),
                "sessions", List.of(mapOf("batch", "1ST", "sessionDate", "2026-01-04", "sessionType", "CLASS",
                        "marks", List.of(Map.of("studentId", "LSA2026/0001", "status", "P"), Map.of("studentId", "LSA2026/0021", "status", "P")))));

        JsonNode r = api.data(api.post(auto, "/api/automation/import", payload).andExpect(status().isOk()));
        assertThat(r.path("studentsCreated").asInt()).isEqualTo(2);
        assertThat(r.path("studentsSkipped").asInt()).as("placeholder without a name").isEqualTo(1);
        assertThat(r.path("paymentsCreated").asInt()).isEqualTo(2);
        assertThat(r.path("paymentsSkipped").asInt()).as("unknown student").isEqualTo(1);
        assertThat(r.path("receiptsCreated").asInt()).isEqualTo(2);
        assertThat(r.path("sessionsCreated").asInt()).isEqualTo(1);
        assertThat(r.path("recordsCreated").asInt()).isEqualTo(1);
        assertThat(r.path("reviewItems").toString()).contains("12-07-20226", "future date", "no date");
        assertThat(r.path("errors").toString()).contains("LSA/2026/0021", "LSA/2026/0099");

        // The attendance variant "LSA2026/0001" landed on the canonical student, and the next new ID follows the imported ones.
        api.get(auto, "/api/automation/search?q=LSA/2026/0001").andExpect(jsonPath("$.data[0].fullName").value("JOSTNA MHATRE"));
        api.get(auto, "/api/automation/lookups").andExpect(jsonPath("$.data.nextStudentId").value("LSA/2026/0016"));
        String created = api.data(api.post(auto, "/api/automation/students", student("New Joiner", "9876543215"))).path("studentId").asText();
        assertThat(created).isEqualTo("LSA/2026/0016");

        // Second run: nothing duplicated.
        JsonNode again = api.data(api.post(auto, "/api/automation/import", payload));
        assertThat(again.path("studentsCreated").asInt()).isZero();
        assertThat(again.path("paymentsCreated").asInt()).isZero();
        assertThat(again.path("recordsCreated").asInt()).isZero();
        api.get(auto, "/api/automation/students?status=ALL").andExpect(jsonPath("$.data.totalElements").value(3));

        // Flagged student shows up for review and is cleared by an edit.
        JsonNode flagged = api.data(api.get(auto, "/api/automation/search?q=SANGITA")).get(0);
        api.get(auto, "/api/automation/students/" + flagged.path("id").asLong()).andExpect(jsonPath("$.data.reviewNote").value(org.hamcrest.Matchers.containsString("12-07-20226")));
        Map<String, Object> fix = student("SANGITA VIKAS MHATRE", "9545188851"); fix.put("admissionDate", "2026-07-12");
        api.put(auto, "/api/automation/students/" + flagged.path("id").asLong(), fix).andExpect(jsonPath("$.data.reviewNote").value(org.hamcrest.Matchers.nullValue()));
    }

    // ---- reports / helpers ---------------------------------------------------------------------------

    @Test
    void reportsRenderAsTablesAndExportAsCsv() throws Exception {
        api.post(auto, "/api/automation/students", student("Karan Shinde", "9876543216")).andExpect(status().isOk());
        api.get(auto, "/api/automation/reports/outstanding").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows.length()").value(1)).andExpect(jsonPath("$.data.totals.balance").value(10000.0));
        api.get(auto, "/api/automation/reports/students/csv").andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("students-")));
        api.get(auto, "/api/automation/reports/nonsense").andExpect(status().isBadRequest());
    }

    @Test
    void helpersProduceTheWorkbookConventions() {
        assertThat(AcadSequenceService.canonicalStudentId("LSA2026/0001")).isEqualTo("LSA/2026/0001");
        assertThat(AcadSequenceService.canonicalStudentId(" lsa/2026/7 ")).isEqualTo("LSA/2026/0007");
        assertThat(AcadSequenceService.canonicalStudentId("ABC/1")).isNull();
        assertThat(AmountInWords.rupees(new BigDecimal("10000"))).isEqualTo("Rupees Ten Thousand Only");
        assertThat(AmountInWords.rupees(new BigDecimal("2500.50"))).isEqualTo("Rupees Two Thousand Five Hundred and Fifty Paise Only");
        assertThat(AmountInWords.rupees(new BigDecimal("125000"))).isEqualTo("Rupees One Lakh Twenty Five Thousand Only");
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
