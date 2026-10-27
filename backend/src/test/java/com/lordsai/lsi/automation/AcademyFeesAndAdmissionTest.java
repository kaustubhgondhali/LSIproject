package com.lordsai.lsi.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.lordsai.lsi.automation.entity.AcadReceipt;
import com.lordsai.lsi.automation.entity.AcadStudent;
import com.lordsai.lsi.automation.repository.AcadReceiptRepository;
import com.lordsai.lsi.automation.repository.AcadStudentRepository;
import com.lordsai.lsi.automation.service.Aadhaar;
import com.lordsai.lsi.automation.service.AcadReceiptNumberAlignment;
import com.lordsai.lsi.email.EmailDelivery;
import com.lordsai.lsi.email.EmailService;
import com.lordsai.lsi.repository.AuditLogRepository;
import com.lordsai.lsi.support.ApiClient;
import com.lordsai.lsi.support.TestAadhaar;
import com.lordsai.lsi.support.TestUsers;
import jakarta.persistence.EntityManager;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Automation Admin (academy office): the compulsory Aadhaar number, receipt numbers that follow
 * the Student ID, a discount for one student, the Pending Fees page with its reminders, and the
 * academy's own course price.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AcademyFeesAndAdmissionTest {

    private static final String STUDENTS = "/api/automation/students";

    @Autowired ApiClient api;
    @Autowired TestUsers users;
    @Autowired AcadStudentRepository students;
    @Autowired AcadReceiptRepository receipts;
    @Autowired AcadReceiptNumberAlignment alignment;
    @Autowired AuditLogRepository auditLogs;
    @Autowired EntityManager em;

    /** Only the SMTP hand-off is stubbed, and only where a test says so. */
    @MockitoSpyBean EmailService emailService;

    private String office;
    private long batch;
    private long course;

    @BeforeEach
    void setUp() throws Exception {
        office = api.login("automation", "Automation@123");
        JsonNode l = api.data(api.get(office, "/api/automation/lookups"));
        batch = l.path("batches").get(0).path("id").asLong();
        course = l.path("courses").get(0).path("id").asLong();
    }

    private Map<String, Object> student(String name, String mobile) {
        Map<String, Object> m = new HashMap<>();
        m.put("fullName", name); m.put("batchId", batch); m.put("courseId", course); m.put("courseFee", 10000);
        m.put("admissionDate", "2026-09-01"); m.put("mobile", mobile);
        m.put("email", name.toLowerCase().replaceAll("[^a-z]", "") + "@example.com"); m.put("address", "Uran, Navi Mumbai");
        m.put("aadhaarNo", TestAadhaar.next());
        return m;
    }

    private JsonNode create(Map<String, Object> body) throws Exception {
        return api.data(api.post(office, STUDENTS, body).andExpect(status().isOk()));
    }

    private JsonNode pay(long studentId, int installment, String date, int amount) throws Exception {
        return api.data(api.post(office, "/api/automation/payments", Map.of("studentId", studentId, "installmentNo", installment,
                "paymentDate", date, "amount", amount, "paymentMode", "CASH")).andExpect(status().isOk()));
    }

    private String pdfText(long receiptId) throws Exception {
        byte[] pdf = api.get(office, "/api/automation/communications/receipts/" + receiptId + "/pdf").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private List<String> activity(long studentId) throws Exception {
        List<String> out = new ArrayList<>();
        api.data(api.get(office, STUDENTS + "/" + studentId + "/profile")).path("activity")
                .forEach(a -> out.add(a.path("action").asText() + " | " + a.path("description").asText()));
        return out;
    }

    // ---- 1. Aadhaar number ----------------------------------------------------------------------

    @Test
    void aadhaarIsRequiredValidUniqueAndOnlyEverShownMasked() throws Exception {
        String valid = TestAadhaar.next();
        Map<String, Object> m = student("Asha Kale", "9876511101");
        m.remove("aadhaarNo");
        api.post(office, STUDENTS, m).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Aadhaar number is required."));
        String wrongCheckDigit = valid.substring(0, 11) + (char) ('0' + (valid.charAt(11) - '0' + 1) % 10);
        for (String bad : new String[]{"123412341234", "2345678901", "23456789012345", wrongCheckDigit, "ABCD12341234"}) {
            m.put("aadhaarNo", bad);
            api.post(office, STUDENTS, m).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(Aadhaar.INVALID));
        }
        // Typed with spaces, as it is printed on the card.
        m.put("aadhaarNo", valid.substring(0, 4) + " " + valid.substring(4, 8) + " " + valid.substring(8));
        JsonNode created = create(m);
        long id = created.path("id").asLong();
        assertThat(created.path("aadhaarMasked").asText()).isEqualTo("XXXX XXXX " + valid.substring(8));
        assertThat(students.findById(id).orElseThrow().getAadhaarNo()).isEqualTo(valid);

        // The full number never leaves the server, and is never written to the activity log.
        String profile = api.get(office, STUDENTS + "/" + id + "/profile").andReturn().getResponse().getContentAsString();
        String list = api.get(office, STUDENTS + "?q=Asha").andReturn().getResponse().getContentAsString();
        assertThat(profile).doesNotContain(valid).contains("XXXX XXXX " + valid.substring(8));
        assertThat(list).doesNotContain(valid);
        assertThat(auditLogs.findAll()).noneMatch(a -> a.getDescription() != null && a.getDescription().contains(valid));

        // One Aadhaar number, one student.
        Map<String, Object> twin = student("Bina Patil", "9876511102");
        twin.put("aadhaarNo", valid);
        api.post(office, STUDENTS, twin).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(created.path("studentId").asText())));

        // Editing: a blank field keeps the saved number.
        Map<String, Object> edit = student("Asha Kale", "9876511101");
        edit.put("aadhaarNo", "");
        api.put(office, STUDENTS + "/" + id, edit).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aadhaarMasked").value("XXXX XXXX " + valid.substring(8)));

        // A student saved before Aadhaar was required is asked for it the next time they are edited.
        AcadStudent older = students.findById(id).orElseThrow();
        older.setAadhaarNo(null);
        students.saveAndFlush(older);
        api.put(office, STUDENTS + "/" + id, edit).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Aadhaar number is required."));
        String given = TestAadhaar.next();
        edit.put("aadhaarNo", given);
        api.put(office, STUDENTS + "/" + id, edit).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aadhaarMasked").value("XXXX XXXX " + given.substring(8)));
    }

    // ---- 2. receipt number = Student ID number --------------------------------------------------

    @Test
    void theReceiptNumberFollowsTheStudentId() throws Exception {
        JsonNode s = create(student("Chetan More", "9876511103"));
        String studentId = s.path("studentId").asText();
        assertThat(studentId).startsWith("LSA/");
        String expected = "LSR" + studentId.substring(3);

        JsonNode p1 = pay(s.path("id").asLong(), 1, "2026-09-02", 2000);
        JsonNode p2 = pay(s.path("id").asLong(), 2, "2026-09-12", 1000);
        assertThat(p1.path("receiptNo").asText()).isEqualTo(expected);
        assertThat(p2.path("receiptNo").asText()).isEqualTo(expected);
        api.get(office, STUDENTS + "/" + s.path("id").asLong() + "/fees").andExpect(jsonPath("$.data.feeReceiptNo").value(expected));
        assertThat(pdfText(p2.path("receiptId").asLong())).contains(expected, studentId);
    }

    @Test
    void receiptsNumberedTheOldWayAreRenumberedToMatchTheirStudentIdAndTheOldNumberIsLogged() throws Exception {
        JsonNode a = create(student("Divya Rane", "9876511111"));
        JsonNode b = create(student("Eshan Gharat", "9876511112"));
        long aId = a.path("id").asLong(), bId = b.path("id").asLong();
        pay(aId, 1, "2026-09-02", 2000);
        pay(aId, 2, "2026-09-09", 2000);
        pay(bId, 1, "2026-09-03", 3000);
        String wantA = "LSR" + a.path("studentId").asText().substring(3);
        String wantB = "LSR" + b.path("studentId").asText().substring(3);

        // As numbered by the old separate counter: A had LSR/2026/0900, and B held what is now A's number.
        oldNumber(aId, "LSR/2026/0900");
        oldNumber(bId, wantA);
        em.clear();

        assertThat(alignment.align()).isEqualTo(2);
        em.flush();
        em.clear();
        assertThat(students.findById(aId).orElseThrow().getFeeReceiptNo()).isEqualTo(wantA);
        assertThat(students.findById(bId).orElseThrow().getFeeReceiptNo()).isEqualTo(wantB);
        assertThat(receipts.findByStudentIdOrderByIssuedAtDesc(aId)).hasSize(2).allMatch(r -> r.getReceiptNo().equals(wantA));
        assertThat(receipts.findByStudentIdOrderByIssuedAtDesc(bId)).hasSize(1).allMatch(r -> r.getReceiptNo().equals(wantB));
        assertThat(activity(aId)).anyMatch(x -> x.startsWith("ACAD_RECEIPT_NUMBER_CHANGED") && x.contains("LSR/2026/0900 -> " + wantA));
        assertThat(activity(bId)).anyMatch(x -> x.startsWith("ACAD_RECEIPT_NUMBER_CHANGED") && x.contains(wantA + " -> " + wantB));

        assertThat(alignment.align()).isZero();   // runs on every start; nothing left to change
    }

    private void oldNumber(long studentId, String number) {
        AcadStudent s = students.findById(studentId).orElseThrow();
        s.setFeeReceiptNo(number);
        students.saveAndFlush(s);
        for (AcadReceipt r : receipts.findByStudentIdOrderByIssuedAtDesc(studentId)) {
            r.setReceiptNo(number);
            receipts.saveAndFlush(r);
        }
    }

    // ---- 5. discount ------------------------------------------------------------------------------

    @Test
    void aDiscountLowersWhatTheStudentPaysAndEveryReceiptShowsIt() throws Exception {
        long id = create(student("Deepa Naik", "9876511104")).path("id").asLong();
        long firstReceipt = pay(id, 1, "2026-09-03", 3000).path("receiptId").asLong();
        String discountUrl = STUDENTS + "/" + id + "/discount";

        // Exactly one of rupees / percent, and a reason.
        api.put(office, discountUrl, Map.of("reason", "x")).andExpect(status().isBadRequest());
        api.put(office, discountUrl, Map.of("amount", 1000, "percent", 10, "reason", "x")).andExpect(status().isBadRequest());
        api.put(office, discountUrl, Map.of("amount", 1000, "reason", " ")).andExpect(status().isBadRequest());
        // At most what is still owed (10,000 - 3,000 paid).
        api.put(office, discountUrl, Map.of("amount", 7001, "reason", "Too much")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("at most ₹7,000")));

        JsonNode f = api.data(api.put(office, discountUrl, Map.of("amount", 2000, "reason", "Sibling discount")).andExpect(status().isOk()));
        assertThat(f.path("courseFee").decimalValue()).isEqualByComparingTo("10000");
        assertThat(f.path("discount").decimalValue()).isEqualByComparingTo("2000");
        assertThat(f.path("netFee").decimalValue()).isEqualByComparingTo("8000");
        assertThat(f.path("balance").decimalValue()).isEqualByComparingTo("5000");
        assertThat(f.path("discountReason").asText()).isEqualTo("Sibling discount");
        assertThat(f.path("discountBy").asText()).isNotBlank();

        // The receipt already issued now shows the discount, under the same number.
        JsonNode r1 = api.data(api.get(office, "/api/automation/receipts/" + firstReceipt));
        assertThat(r1.path("totalFee").decimalValue()).isEqualByComparingTo("10000");
        assertThat(r1.path("discount").decimalValue()).isEqualByComparingTo("2000");
        assertThat(r1.path("netFee").decimalValue()).isEqualByComparingTo("8000");
        assertThat(r1.path("balance").decimalValue()).isEqualByComparingTo("5000");
        assertThat(pdfText(firstReceipt)).contains("Course Fee", "10,000", "Discount", "2,000", "Fee after Discount (to pay)", "8,000", "5,000");

        // Lists use the fee after discount.
        JsonNode row = api.data(api.get(office, STUDENTS + "/" + id));
        assertThat(row.path("discount").decimalValue()).isEqualByComparingTo("2000");
        assertThat(row.path("netFee").decimalValue()).isEqualByComparingTo("8000");
        assertThat(row.path("balance").decimalValue()).isEqualByComparingTo("5000");
        assertThat(row.path("paymentStatus").asText()).isEqualTo("PARTIAL");

        // A payment cannot go over the balance after discount; paying it in full makes the student PAID.
        api.post(office, "/api/automation/payments", Map.of("studentId", id, "installmentNo", 2, "paymentDate", "2026-09-10",
                "amount", 5001, "paymentMode", "CASH")).andExpect(status().isBadRequest());
        long secondReceipt = pay(id, 2, "2026-09-10", 5000).path("receiptId").asLong();
        row = api.data(api.get(office, STUDENTS + "/" + id));
        assertThat(row.path("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(row.path("balance").decimalValue()).isEqualByComparingTo("0");
        assertThat(pdfText(secondReceipt)).contains("Discount", "Fee after Discount (to pay)");

        // Change to 10%: a percentage of the course fee; both receipts follow, each with its own balance.
        f = api.data(api.put(office, discountUrl, Map.of("percent", 10, "reason", "Festival offer")).andExpect(status().isOk()));
        assertThat(f.path("discount").decimalValue()).isEqualByComparingTo("1000");
        assertThat(f.path("balance").decimalValue()).isEqualByComparingTo("1000");
        assertThat(api.data(api.get(office, "/api/automation/receipts/" + firstReceipt)).path("balance").decimalValue()).isEqualByComparingTo("6000");
        assertThat(api.data(api.get(office, "/api/automation/receipts/" + secondReceipt)).path("balance").decimalValue()).isEqualByComparingTo("1000");

        // The fee cannot be edited below what the discount and the payments already need.
        Map<String, Object> edit = student("Deepa Naik", "9876511104");
        edit.put("aadhaarNo", "");
        edit.put("courseFee", 8500);
        api.put(office, STUDENTS + "/" + id, edit).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Change or remove the discount first")));

        // Remove: back to the full fee.
        f = api.data(api.delete(office, discountUrl).andExpect(status().isOk()));
        assertThat(f.path("discount").decimalValue()).isEqualByComparingTo("0");
        assertThat(f.path("balance").decimalValue()).isEqualByComparingTo("2000");
        assertThat(pdfText(firstReceipt)).doesNotContain("Fee after Discount");
        api.delete(office, discountUrl).andExpect(status().isBadRequest());

        assertThat(activity(id)).anyMatch(x -> x.startsWith("ACAD_DISCOUNT_GIVEN") && x.contains("Sibling discount"))
                .anyMatch(x -> x.startsWith("ACAD_DISCOUNT_CHANGED") && x.contains("10% of"))
                .anyMatch(x -> x.startsWith("ACAD_DISCOUNT_REMOVED"));
    }

    @Test
    void deletingOneStudentRemovesOnlyTheirOwnRecords() throws Exception {
        long a = create(student("Rohan Delete", "9876511106")).path("id").asLong();
        long b = create(student("Sana Keep", "9876511107")).path("id").asLong();
        String codeA = api.data(api.get(office, STUDENTS + "/" + a)).path("studentId").asText();
        pay(a, 1, "2026-09-01", 3000); pay(a, 2, "2026-09-08", 1000);
        long receiptB = pay(b, 1, "2026-09-01", 2500).path("receiptId").asLong();
        api.put(office, STUDENTS + "/" + a + "/discount", Map.of("amount", 500, "reason", "A")).andExpect(status().isOk());
        api.put(office, STUDENTS + "/" + b + "/discount", Map.of("amount", 700, "reason", "B")).andExpect(status().isOk());
        long session = api.data(api.post(office, "/api/automation/attendance/sessions", Map.of("batchId", batch, "sessionDate", "2026-09-06")))
                .path("session").path("id").asLong();
        api.post(office, "/api/automation/attendance/save", Map.of("sessionId", session, "marks", List.of(
                Map.of("studentId", a, "status", "PRESENT"), Map.of("studentId", b, "status", "PRESENT")))).andExpect(status().isOk());

        // The Student ID must be typed to confirm.
        api.delete(office, STUDENTS + "/" + a + "/permanent").andExpect(status().isBadRequest());
        api.delete(office, STUDENTS + "/" + a + "/permanent?confirm=WRONG").andExpect(status().isBadRequest());
        api.get(office, STUDENTS + "/" + a).andExpect(status().isOk());

        api.delete(office, STUDENTS + "/" + a + "/permanent?confirm=" + codeA.toLowerCase()).andExpect(status().isOk());
        api.get(office, STUDENTS + "/" + a).andExpect(status().isNotFound());
        assertThat(api.data(api.get(office, "/api/automation/payments")).findValuesAsText("studentCode")).doesNotContain(codeA);

        // The other student is exactly as before.
        JsonNode pb = api.data(api.get(office, STUDENTS + "/" + b + "/profile"));
        assertThat(pb.path("payments")).hasSize(1);
        assertThat(pb.path("receipts")).hasSize(1);
        assertThat(pb.path("fees").path("discount").decimalValue()).isEqualByComparingTo("700");
        assertThat(pb.path("fees").path("balance").decimalValue()).isEqualByComparingTo("6800");
        assertThat(pb.path("attendance").path("sessionsTotal").asInt()).isEqualTo(1);
        assertThat(api.data(api.get(office, "/api/automation/receipts/" + receiptB)).path("balance").decimalValue()).isEqualByComparingTo("6800");
        assertThat(pdfText(receiptB)).contains("Sana Keep");
        assertThat(auditLogs.findAll()).anyMatch(x -> "ACAD_STUDENT_DELETED".equals(x.getAction()) && x.getDescription().contains(codeA));
    }

    @Test
    void aDiscountAtALaterInstallmentShowsOnlyFromThatReceiptOn() throws Exception {
        long id = create(student("Kiran Patil", "9876511105")).path("id").asLong();
        String discountUrl = STUDENTS + "/" + id + "/discount";
        long r1 = pay(id, 1, "2026-09-01", 2000).path("receiptId").asLong();
        long r2 = pay(id, 2, "2026-09-08", 2000).path("receiptId").asLong();

        // Only an installment already paid or the next one (3rd).
        api.put(office, discountUrl, Map.of("amount", 500, "reason", "x", "installmentNo", 4)).andExpect(status().isBadRequest());

        // Discount at the 3rd installment: the balance drops now, receipts 1 and 2 stay as they were.
        JsonNode f = api.data(api.put(office, discountUrl, Map.of("amount", 1000, "reason", "Loyalty", "installmentNo", 3)).andExpect(status().isOk()));
        assertThat(f.path("discount").decimalValue()).isEqualByComparingTo("1000");
        assertThat(f.path("balance").decimalValue()).isEqualByComparingTo("5000");
        assertThat(f.path("discounts")).hasSize(1);
        assertThat(f.path("discounts").get(0).path("installmentNo").asInt()).isEqualTo(3);
        JsonNode rec2 = api.data(api.get(office, "/api/automation/receipts/" + r2));
        assertThat(rec2.path("discount").decimalValue()).isEqualByComparingTo("0");
        assertThat(rec2.path("balance").decimalValue()).isEqualByComparingTo("6000");
        assertThat(pdfText(r2)).doesNotContain("Discount");

        long r3 = pay(id, 3, "2026-09-15", 2000).path("receiptId").asLong();
        JsonNode rec3 = api.data(api.get(office, "/api/automation/receipts/" + r3));
        assertThat(rec3.path("discount").decimalValue()).isEqualByComparingTo("1000");
        assertThat(rec3.path("netFee").decimalValue()).isEqualByComparingTo("9000");
        assertThat(rec3.path("balance").decimalValue()).isEqualByComparingTo("3000");
        assertThat(pdfText(r3)).contains("Discount (3rd Installment)", "1,000", "Fee after Discount (to pay)", "9,000");

        // A second discount at the 4th installment; together they can be at most what is still owed.
        api.put(office, discountUrl, Map.of("amount", 3001, "reason", "x", "installmentNo", 4)).andExpect(status().isBadRequest());
        api.put(office, discountUrl, Map.of("amount", 500, "reason", "Topper", "installmentNo", 4)).andExpect(status().isOk());
        long r4 = pay(id, 4, "2026-09-22", 1000).path("receiptId").asLong();
        assertThat(api.data(api.get(office, "/api/automation/receipts/" + r3)).path("discount").decimalValue()).isEqualByComparingTo("1000");
        JsonNode rec4 = api.data(api.get(office, "/api/automation/receipts/" + r4));
        assertThat(rec4.path("discount").decimalValue()).isEqualByComparingTo("1500");
        assertThat(rec4.path("balance").decimalValue()).isEqualByComparingTo("1500");
        assertThat(pdfText(r4)).contains("Discount (3rd Installment)", "Discount (4th Installment)", "8,500");
        assertThat(api.data(api.get(office, "/api/automation/receipts/" + r1)).path("discount").decimalValue()).isEqualByComparingTo("0");

        // Removing the 3rd installment's discount leaves the 4th's.
        f = api.data(api.delete(office, discountUrl + "?installmentNo=3").andExpect(status().isOk()));
        assertThat(f.path("discount").decimalValue()).isEqualByComparingTo("500");
        assertThat(api.data(api.get(office, "/api/automation/receipts/" + r3)).path("discount").decimalValue()).isEqualByComparingTo("0");
        assertThat(api.data(api.get(office, "/api/automation/receipts/" + r4)).path("balance").decimalValue()).isEqualByComparingTo("2500");
        api.delete(office, discountUrl + "?installmentNo=3").andExpect(status().isBadRequest());
    }

    // ---- 4. pending fees ---------------------------------------------------------------------------

    @Test
    void pendingFeesListsWhoOwesWithTheirContactsAndSendsReminders() throws Exception {
        long partly = create(student("Esha Jadhav", "9876511105")).path("id").asLong();
        pay(partly, 1, "2026-09-04", 4000);
        long nothing = create(student("Farhan Shaikh", "9876511106")).path("id").asLong();
        long paid = create(student("Gauri Mane", "9876511107")).path("id").asLong();
        pay(paid, 1, "2026-09-05", 10000);
        long archived = create(student("Harsh Vora", "9876511108")).path("id").asLong();
        api.patch(office, STUDENTS + "/" + archived + "/archive", Map.of()).andExpect(status().isOk());

        JsonNode list = api.data(api.get(office, "/api/automation/pending-fees"));
        List<Long> ids = new ArrayList<>();
        list.path("rows").forEach(r -> ids.add(r.path("id").asLong()));
        assertThat(ids).containsSubsequence(nothing, partly).doesNotContain(paid, archived);   // highest balance first
        JsonNode esha = null;
        for (JsonNode r : list.path("rows")) {
            if (r.path("id").asLong() == partly) {
                esha = r;
            }
        }
        assertThat(esha).isNotNull();
        assertThat(esha.path("mobile").asText()).isEqualTo("9876511105");
        assertThat(esha.path("email").asText()).isEqualTo("eshajadhav@example.com");
        assertThat(esha.path("address").asText()).isEqualTo("Uran, Navi Mumbai");
        assertThat(esha.path("balance").decimalValue()).isEqualByComparingTo("6000");
        assertThat(esha.path("paymentStatus").asText()).isEqualTo("PARTIAL");
        assertThat(esha.path("installmentsPaid").asInt()).isEqualTo(1);
        assertThat(esha.path("lastPaymentDate").asText()).isEqualTo("2026-09-04");
        assertThat(esha.path("lastReminderAt").isNull()).isTrue();
        assertThat(list.path("students").asInt()).isEqualTo(list.path("rows").size());

        api.get(office, "/api/automation/pending-fees?q=Esha").andExpect(jsonPath("$.data.rows.length()").value(1));
        api.get(office, "/api/automation/pending-fees?paymentStatus=PENDING")
                .andExpect(jsonPath("$.data.rows[?(@.id == " + partly + ")]").isEmpty())
                .andExpect(jsonPath("$.data.rows[?(@.id == " + nothing + ")]").isNotEmpty());

        // Print / PDF / Excel use the same list.
        api.get(office, "/api/automation/reports/pending-fees").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columns").value(org.hamcrest.Matchers.hasItems("Mobile", "Email", "Address", "Balance")));
        api.get(office, "/api/automation/reports/pending-fees/xlsx").andExpect(status().isOk());

        // WhatsApp: the chat the click opens, recorded as opened.
        JsonNode wa = api.data(api.post(office, "/api/automation/pending-fees/" + partly + "/remind", Map.of("channel", "WHATSAPP"))
                .andExpect(status().isOk()));
        assertThat(wa.path("status").asText()).isEqualTo("OPENED");
        assertThat(wa.path("whatsappChat").path("phone").asText()).isEqualTo("919876511105");
        String text = wa.path("whatsappChat").path("text").asText();
        assertThat(text).startsWith("Dear Esha Jadhav,").contains("₹6,000", "fee ₹10,000", "paid so far ₹4,000");

        // Email without a mail server is reported honestly; with one, it is sent.
        JsonNode failed = api.data(api.post(office, "/api/automation/pending-fees/" + partly + "/remind", Map.of("channel", "EMAIL")));
        assertThat(failed.path("status").asText()).isEqualTo("FAILED");
        doReturn(EmailDelivery.sent("OFFICE-SMTP")).when(emailService).sendMessage(anyString(), anyString(), anyString(), anyString(), any());
        JsonNode sent = api.data(api.post(office, "/api/automation/pending-fees/" + partly + "/remind", Map.of("channel", "EMAIL")));
        assertThat(sent.path("status").asText()).isEqualTo("SENT");
        assertThat(sent.path("message").asText()).isEqualTo("Reminder emailed to eshajadhav@example.com.");

        // The list shows when the student was last reminded; Message History has the reminders.
        JsonNode after = api.data(api.get(office, "/api/automation/pending-fees?q=Esha")).path("rows").get(0);
        assertThat(after.path("lastReminderAt").isNull()).isFalse();
        JsonNode history = api.data(api.get(office, "/api/automation/communications/history?studentId=" + partly));
        assertThat(history.path("totalElements").asInt()).isEqualTo(3);
        history.path("content").forEach(h -> assertThat(h.path("messageType").asText()).isEqualTo("FEE_REMINDER"));
        // "Open again" from Message History reopens the same reminder.
        long waLog = wa.path("logId").asLong();
        JsonNode reopened = api.data(api.post(office, "/api/automation/communications/history/" + waLog + "/retry", null));
        assertThat(reopened.path("whatsappChat").path("text").asText()).isEqualTo(text);

        // Nothing to remind a student who has paid.
        api.post(office, "/api/automation/pending-fees/" + paid + "/remind", Map.of("channel", "WHATSAPP")).andExpect(status().isBadRequest());
        api.post(office, "/api/automation/pending-fees/" + partly + "/remind", Map.of()).andExpect(status().isBadRequest());

        // Office only.
        users.student("pupil.pending@test.local");
        String pupil = api.login("pupil.pending@test.local", TestUsers.PASSWORD);
        api.get(pupil, "/api/automation/pending-fees").andExpect(status().isForbidden());
        api.get(null, "/api/automation/pending-fees").andExpect(status().isUnauthorized());
    }

    // ---- 6. course price -----------------------------------------------------------------------------

    @Test
    void theAcademyCoursePriceAppliesToNewAdmissionsOnly() throws Exception {
        Map<String, Object> before = student("Ira Kulkarni", "9876511109");
        before.remove("courseFee");   // blank = the course price
        JsonNode admitted = create(before);
        java.math.BigDecimal oldPrice = admitted.path("courseFee").decimalValue();

        JsonNode saved = api.data(api.patch(office, "/api/automation/courses/" + course + "/price", Map.of("price", 12500)).andExpect(status().isOk()));
        assertThat(saved.path("defaultFee").decimalValue()).isEqualByComparingTo("12500");
        JsonNode lookupCourse = null;
        for (JsonNode c : api.data(api.get(office, "/api/automation/lookups")).path("courses")) {
            if (c.path("id").asLong() == course) {
                lookupCourse = c;
            }
        }
        assertThat(lookupCourse).isNotNull();
        assertThat(lookupCourse.path("defaultFee").decimalValue()).isEqualByComparingTo("12500");

        Map<String, Object> after = student("Jay Pawar", "9876511110");
        after.remove("courseFee");
        assertThat(create(after).path("courseFee").decimalValue()).isEqualByComparingTo("12500");
        // The student admitted before keeps the fee they were admitted with.
        assertThat(api.data(api.get(office, STUDENTS + "/" + admitted.path("id").asLong())).path("courseFee").decimalValue())
                .isEqualByComparingTo(oldPrice);
        assertThat(auditLogs.findAll()).anyMatch(a -> "ACAD_COURSE_PRICE_CHANGED".equals(a.getAction()) && a.getDescription().contains("12,500"));

        api.patch(office, "/api/automation/courses/" + course + "/price", Map.of("price", -1)).andExpect(status().isBadRequest());
        api.patch(office, "/api/automation/courses/" + course + "/price", Map.of()).andExpect(status().isBadRequest());
        users.student("pupil.price@test.local");
        String pupil = api.login("pupil.price@test.local", TestUsers.PASSWORD);
        api.patch(pupil, "/api/automation/courses/" + course + "/price", Map.of("price", 1)).andExpect(status().isForbidden());
    }
}
