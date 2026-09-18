package com.lordsai.lsi.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.lordsai.lsi.automation.repository.AcadCommunicationLogRepository;
import com.lordsai.lsi.email.EmailDelivery;
import com.lordsai.lsi.email.EmailService;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.CommunicationStatus;
import com.lordsai.lsi.repository.AuditLogRepository;
import com.lordsai.lsi.repository.CommunicationLogRepository;
import com.lordsai.lsi.support.ApiClient;
import com.lordsai.lsi.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Email & WhatsApp automation lives in the Automation Admin and works ONLY on its own students
 * (acad_students) and its own log (acad_communication_logs): recipients & filters, individual /
 * selected / filtered sends, preview, batching, failure tracking, retry, history and isolation
 * from website / LMS accounts.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AutomationCommunicationTest {

    @Autowired ApiClient api;
    @Autowired TestUsers users;
    @Autowired MockMvc mockMvc;
    @Autowired AcadCommunicationLogRepository acadLogs;
    @Autowired CommunicationLogRepository lmsLogs;
    @Autowired AuditLogRepository auditLogRepository;

    /** Spied so tests can simulate a working mail channel without SMTP. */
    @MockitoSpyBean EmailService emailService;

    private String office;
    private long batch1, batch2, course1;
    private long ravi, sneha, pooja;

    @BeforeEach
    void setUp() throws Exception {
        office = api.login("automation", "Automation@123");
        JsonNode l = api.data(api.get(office, "/api/automation/lookups"));
        batch1 = l.path("batches").get(0).path("id").asLong();
        batch2 = l.path("batches").size() > 1 ? l.path("batches").get(1).path("id").asLong()
                : api.data(api.post(office, "/api/automation/batches", Map.of("name", "Evening", "schedule", "6-8 PM"))).path("id").asLong();
        course1 = l.path("courses").get(0).path("id").asLong();

        ravi = create("Ravi Patil", "9876543210", "ravi@office.local", batch1);
        sneha = create("Sneha More", "9876543211", "sneha@office.local", batch1);
        pooja = create("Pooja Thakur", "9876543212", null, batch2);  // mobile only, no email

        // A website / LMS student that must NEVER appear in the automation recipient list.
        users.student("portal.student@example.com");
    }

    private long create(String name, String mobile, String email, long batchId) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("fullName", name); m.put("batchId", batchId); m.put("courseId", course1);
        m.put("admissionDate", "2026-09-01"); m.put("mobile", mobile);
        if (email != null) m.put("email", email);
        return api.data(api.post(office, "/api/automation/students", m)).path("id").asLong();
    }

    // ---- recipients: Automation Admin data only -------------------------------------------------

    @Test
    void recipientsAreTheAcademysOwnStudentsNeverWebsiteAccounts() throws Exception {
        JsonNode all = api.data(api.get(office, "/api/automation/communications/students"));
        List<String> names = all.path("content").findValuesAsText("fullName");
        assertThat(names).containsExactlyInAnyOrder("Ravi Patil", "Sneha More", "Pooja Thakur");
        assertThat(all.path("content").findValuesAsText("email")).doesNotContain("portal.student@example.com");
        assertThat(all.path("content").get(0).has("courses")).as("no LMS purchase data in the recipient row").isFalse();

        assertThat(api.data(api.get(office, "/api/automation/communications/students?batchId=" + batch2)).path("totalElements").asLong()).isEqualTo(1);
        assertThat(api.data(api.get(office, "/api/automation/communications/students?q=sneha")).path("content").get(0).path("fullName").asText()).isEqualTo("Sneha More");

        api.patch(office, "/api/automation/students/" + pooja + "/archive", Map.of("archived", true)).andExpect(status().isOk());
        assertThat(api.data(api.get(office, "/api/automation/communications/students")).path("totalElements").asLong()).as("archived students are not messaged by default").isEqualTo(2);
        assertThat(api.data(api.get(office, "/api/automation/communications/students?status=ALL")).path("totalElements").asLong()).isEqualTo(3);
    }

    @Test
    void previewCountsUsableAddressesAndReportsWhatsAppConfiguration() throws Exception {
        api.post(office, "/api/automation/communications/preview", Map.of("channels", List.of("EMAIL", "WHATSAPP"), "message", "Hi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recipients").value(3))
                .andExpect(jsonPath("$.data.emailRecipients").value(2))
                .andExpect(jsonPath("$.data.whatsappRecipients").value(3))
                .andExpect(jsonPath("$.data.whatsappConfigured").value(false));
    }

    // ---- sending ----------------------------------------------------------------------------------

    @Test
    void individualEmailIsDeliveredAndLoggedInTheAutomationLogOnly() throws Exception {
        doReturn(EmailDelivery.sent("OFFICE-SMTP")).when(emailService).sendMessage(eq("ravi@office.local"), anyString(), anyString(), anyString(), any());
        api.post(office, "/api/automation/communications/send", Map.of(
                        "studentIds", List.of(ravi), "channels", List.of("EMAIL"),
                        "subject", "Batch timing", "message", "Class starts at 10 AM."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recipients").value(1))
                .andExpect(jsonPath("$.data.emailQueued").value(1))
                .andExpect(jsonPath("$.data.message").value("Sent 1 message(s)."));

        JsonNode history = api.data(api.get(office, "/api/automation/communications/history?channel=EMAIL"));
        assertThat(history.path("totalElements").asLong()).isEqualTo(1);
        JsonNode row = history.path("content").get(0);
        assertThat(row.path("status").asText()).isEqualTo("SENT");
        assertThat(row.path("messageType").asText()).isEqualTo("ADMIN_INDIVIDUAL");
        assertThat(row.path("provider").asText()).isEqualTo("OFFICE-SMTP");
        assertThat(row.path("studentCode").asText()).startsWith("LSA/");
        assertThat(row.path("studentName").asText()).isEqualTo("Ravi Patil");
        assertThat(row.path("studentId").asLong()).isEqualTo(ravi);
        assertThat(row.path("batchRef").asText()).startsWith("AUTO-");

        // Isolation: the online-academy log (purchase / invoice emails) is untouched.
        assertThat(acadLogs.count()).isEqualTo(1);
        assertThat(lmsLogs.count()).isZero();
        assertThat(auditLogRepository.findAll().stream().map(a -> a.getAction()).toList())
                .contains("AUTOMATION_MESSAGE_QUEUED", "AUTOMATION_EMAIL_SENT");
        verify(emailService, times(1)).sendMessage(eq("ravi@office.local"), eq("Ravi Patil"), eq("Batch timing"), eq("Class starts at 10 AM."), any());
    }

    @Test
    void whatsAppFailsHonestlyWhenNoProviderIsConfigured() throws Exception {
        api.post(office, "/api/automation/communications/send", Map.of("studentIds", List.of(sneha), "channels", List.of("WHATSAPP"), "message", "Hi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.whatsappQueued").value(1))
                .andExpect(jsonPath("$.data.message").value(org.hamcrest.Matchers.containsString("not configured")));
        JsonNode row = api.data(api.get(office, "/api/automation/communications/history?channel=WHATSAPP&status=FAILED")).path("content").get(0);
        assertThat(row.path("recipient").asText()).isEqualTo("9876543211");
        assertThat(row.path("errorReason").asText()).isNotBlank();
    }

    @Test
    void filteredBulkSendQueuesOneRowPerStudentPerChannelAndSkipsMissingAddresses() throws Exception {
        JsonNode r = api.data(api.post(office, "/api/automation/communications/send", Map.of(
                "filter", Map.of("status", "ALL"), "channels", List.of("EMAIL", "WHATSAPP"), "subject", "Holiday", "message", "Closed on Monday.")));
        assertThat(r.path("recipients").asInt()).isEqualTo(3);
        assertThat(r.path("emailQueued").asInt()).isEqualTo(2);
        assertThat(r.path("whatsappQueued").asInt()).isEqualTo(3);
        assertThat(r.path("skipped").asInt()).as("Pooja has no email").isEqualTo(1);
        String batchRef = r.path("batchRef").asText();

        api.get(office, "/api/automation/communications/batches/" + batchRef)
                .andExpect(jsonPath("$.data.total").value(5))
                .andExpect(jsonPath("$.data.pending").value(0));
        assertThat(acadLogs.findByBatchRefOrderByIdAsc(batchRef)).hasSize(5)
                .allMatch(l -> l.getMessageType().name().equals("ADMIN_BULK"));
        assertThat(api.data(api.get(office, "/api/automation/communications/students/" + ravi + "/history")).size()).isEqualTo(2);
    }

    @Test
    void emailRequiresSubjectAndEmptySelectionIsRejected() throws Exception {
        api.post(office, "/api/automation/communications/send", Map.of("studentIds", List.of(ravi), "channels", List.of("EMAIL"), "message", "no subject"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A subject is required for email."));
        api.post(office, "/api/automation/communications/send", Map.of("filter", Map.of("q", "nobody-matches"), "channels", List.of("EMAIL"), "subject", "s", "message", "m"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No students match the selection."));
        verify(emailService, never()).sendMessage(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void failedEmailCanBeRetriedOnceMailWorks() throws Exception {
        JsonNode result = api.data(api.post(office, "/api/automation/communications/send", Map.of(
                "studentIds", List.of(sneha), "channels", List.of("EMAIL"), "subject", "Retry me", "message", "Body")));
        long logId = acadLogs.findByBatchRefOrderByIdAsc(result.path("batchRef").asText()).get(0).getId();
        api.get(office, "/api/automation/communications/history/" + logId)
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.attempts").value(1));

        doReturn(EmailDelivery.sent("OFFICE-SMTP")).when(emailService).sendMessage(eq("sneha@office.local"), anyString(), anyString(), anyString(), any());
        api.post(office, "/api/automation/communications/history/" + logId + "/retry", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SENT"))
                .andExpect(jsonPath("$.data.attempts").value(2))
                .andExpect(jsonPath("$.data.errorReason").doesNotExist());
        api.post(office, "/api/automation/communications/history/" + logId + "/retry", null).andExpect(status().isConflict());
        assertThat(acadLogs.findById(logId).orElseThrow().getStatus()).isEqualTo(CommunicationStatus.SENT);
    }

    @Test
    void attachmentIsValidatedUploadedAndPassedToTheEmail() throws Exception {
        mockMvc.perform(multipart("/api/automation/communications/attachments")
                        .file(new MockMultipartFile("file", "script.exe", "application/octet-stream", new byte[10]))
                        .header("Authorization", "Bearer " + office))
                .andExpect(status().isBadRequest());
        JsonNode up = api.data(mockMvc.perform(multipart("/api/automation/communications/attachments")
                .file(new MockMultipartFile("file", "timetable.pdf", "application/pdf", "%PDF-1.4 test".getBytes()))
                .header("Authorization", "Bearer " + office)).andExpect(status().isOk()));
        assertThat(up.path("path").asText()).startsWith("attachments/");

        doReturn(EmailDelivery.sent("OFFICE-SMTP")).when(emailService).sendMessage(anyString(), anyString(), anyString(), anyString(), any());
        api.post(office, "/api/automation/communications/send", Map.of("studentIds", List.of(ravi), "channels", List.of("EMAIL"),
                        "subject", "Timetable", "message", "Attached.", "attachmentPath", up.path("path").asText(), "attachmentName", "timetable.pdf"))
                .andExpect(status().isOk());
        verify(emailService).sendMessage(eq("ravi@office.local"), anyString(), eq("Timetable"), eq("Attached."),
                org.mockito.ArgumentMatchers.argThat(a -> a != null && "timetable.pdf".equals(a.filename())));

        api.post(office, "/api/automation/communications/send", Map.of("studentIds", List.of(ravi), "channels", List.of("EMAIL"),
                        "subject", "x", "message", "y", "attachmentPath", "attachments/../secret.pdf"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void historyExportAndCountersComeFromTheAutomationLog() throws Exception {
        api.post(office, "/api/automation/communications/send", Map.of("studentIds", List.of(ravi), "channels", List.of("EMAIL"), "subject", "A", "message", "B"))
                .andExpect(status().isOk());
        api.get(office, "/api/automation/reports/communications").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Automation Message History"))
                .andExpect(jsonPath("$.data.rows.length()").value(1));
        api.get(office, "/api/automation/reports/communications?status=SENT").andExpect(jsonPath("$.data.rows.length()").value(0));
        api.get(office, "/api/automation/communications/counters").andExpect(jsonPath("$.data.failed").value(1));
    }

    // ---- security -----------------------------------------------------------------------------------

    @Test
    void onlyAutomationRolesReachTheAutomationEndpoints() throws Exception {
        User student = users.student("pupil@example.com");
        String studentToken = api.login("pupil@example.com", TestUsers.PASSWORD);
        api.get(studentToken, "/api/automation/communications/students").andExpect(status().isForbidden());
        api.post(studentToken, "/api/automation/communications/send", Map.of("studentIds", List.of(ravi), "channels", List.of("EMAIL"), "subject", "s", "message", "m"))
                .andExpect(status().isForbidden());
        api.get(null, "/api/automation/communications/history").andExpect(status().isUnauthorized());
        assertThat(student.getId()).isNotNull();
    }
}
