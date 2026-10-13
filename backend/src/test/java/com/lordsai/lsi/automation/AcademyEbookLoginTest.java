package com.lordsai.lsi.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.lordsai.lsi.automation.entity.AcadStudent;
import com.lordsai.lsi.automation.repository.AcadStudentRepository;
import com.lordsai.lsi.automation.service.AcadNewEbookGrant;
import com.lordsai.lsi.email.EmailDelivery;
import com.lordsai.lsi.email.EmailService;
import com.lordsai.lsi.entity.Ebook;
import com.lordsai.lsi.entity.StudentProfile;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.EbookStatus;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.repository.EbookRepository;
import com.lordsai.lsi.repository.StudentProfileRepository;
import com.lordsai.lsi.service.EbookActivatedEvent;
import com.lordsai.lsi.service.EbookEntitlementService;
import com.lordsai.lsi.service.EbookService;
import com.lordsai.lsi.service.StudentIdRepair;
import com.lordsai.lsi.support.ApiClient;
import com.lordsai.lsi.support.TestAadhaar;
import com.lordsai.lsi.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Automation Admin students' free ebook login: made when the first installment is recorded, it
 * signs in to the Student Panel with its own User ID and password, keeps the academy Student ID
 * (LSA/...), opens only the ebook, and can be reset, emailed and blocked by the office.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
class AcademyEbookLoginTest {

    @Autowired ApiClient api;
    @Autowired TestUsers users;
    @Autowired AcadStudentRepository students;
    @Autowired StudentProfileRepository profiles;
    @Autowired EbookRepository ebooks;
    @Autowired EbookEntitlementService entitlements;
    @Autowired StudentIdRepair studentIdRepair;
    @Autowired EbookService ebookService;
    @Autowired AcadNewEbookGrant newEbookGrant;
    @Autowired ApplicationEvents events;

    /** Only the SMTP hand-off is stubbed. */
    @MockitoSpyBean EmailService emailService;

    private String office;
    private long batch;
    private long course;
    private Ebook ebook;

    @BeforeEach
    void setUp() throws Exception {
        office = api.login("automation", "Automation@123");
        JsonNode l = api.data(api.get(office, "/api/automation/lookups"));
        batch = l.path("batches").get(0).path("id").asLong();
        course = l.path("courses").get(0).path("id").asLong();
        ebook = new Ebook();
        ebook.setEbookCode("EB-ACADEMY");
        ebook.setTitle("Share Market Basics Ebook");
        ebook.setPrice(new BigDecimal("499.00"));
        ebook.setPdfPath("ebooks/academy.pdf");
        ebook.setStatus(EbookStatus.ACTIVE);
        ebook = ebooks.saveAndFlush(ebook);
    }

    private JsonNode student(String name, String mobile, String email) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("fullName", name); m.put("batchId", batch); m.put("courseId", course); m.put("courseFee", 10000);
        m.put("admissionDate", "2026-09-01"); m.put("mobile", mobile); m.put("email", email); m.put("address", "Uran, Navi Mumbai");
        m.put("aadhaarNo", TestAadhaar.next());
        return api.data(api.post(office, "/api/automation/students", m).andExpect(status().isOk()));
    }

    private String pay(long studentId, int installment, int amount) throws Exception {
        return api.post(office, "/api/automation/payments", Map.of("studentId", studentId, "installmentNo", installment,
                "paymentDate", "2026-10-0" + installment, "amount", amount, "paymentMode", "CASH")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private JsonNode access(long studentId) throws Exception {
        return api.data(api.get(office, "/api/automation/students/" + studentId + "/ebook-access"));
    }

    private void emailWorks() {
        doReturn(EmailDelivery.sent("OFFICE-SMTP")).when(emailService).sendAcademyEbookLogin(any(), anyString(), anyString(), anyString());
    }

    @Test
    void theFirstInstallmentMakesAnEbookOnlyLoginWithTheAcademyStudentId() throws Exception {
        emailWorks();
        JsonNode s = student("Kiran Mhatre", "9876522201", "kiran.mhatre@example.com");
        long id = s.path("id").asLong();
        String studentId = s.path("studentId").asText();
        assertThat(access(id).path("state").asText()).isEqualTo("NO_PAYMENT");

        String paid = pay(id, 1, 3000);
        assertThat(paid).contains("Ebook login created: User ID");
        JsonNode a = access(id);
        assertThat(a.path("state").asText()).isEqualTo("ACTIVE");
        assertThat(a.path("studentId").asText()).isEqualTo(studentId);
        String userId = a.path("userId").asText();
        String password = a.path("password").asText();
        assertThat(userId).startsWith("kiran");
        assertThat(password).hasSize(12);
        assertThat(a.path("emailedAt").isNull()).isFalse();
        assertThat(a.path("linkedAccount").asBoolean()).isFalse();
        assertThat(a.path("ebooks")).hasSize(1);
        assertThat(a.path("ebooks").get(0).path("status").asText()).isEqualTo("ACTIVE");
        assertThat(a.path("ebooks").get(0).path("source").asText()).isEqualTo("ACADEMY");
        ArgumentCaptor<String> sentPassword = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendAcademyEbookLogin(any(), org.mockito.ArgumentMatchers.eq(userId), sentPassword.capture(), anyString());
        assertThat(sentPassword.getValue()).isEqualTo(password);

        // The student signs in with the User ID and password: Student Panel, ebook only, academy Student ID.
        String pupil = api.login(userId, password);
        JsonNode me = api.data(api.get(pupil, "/api/student/profile"));
        assertThat(me.path("studentId").asText()).isEqualTo(studentId);
        assertThat(me.path("loginUserId").asText()).isEqualTo(userId);
        assertThat(me.path("ebookOnly").asBoolean()).isTrue();
        assertThat(api.data(api.get(pupil, "/api/student/courses"))).isEmpty();
        JsonNode mine = api.data(api.get(pupil, "/api/student/ebooks"));
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).path("status").asText()).isEqualTo("ACTIVE");
        assertThat(mine.get(0).path("source").asText()).isEqualTo("ACADEMY");
        User portal = students.findById(id).orElseThrow().getPortalUser();
        assertThat(entitlements.requireAccess(portal.getId(), ebook.getId())).isNotNull();

        // A later installment makes no second login; an ebook put on sale later is added then.
        Ebook second = new Ebook();
        second.setEbookCode("EB-ACADEMY-2");
        second.setTitle("Option Trading Ebook");
        second.setPrice(new BigDecimal("299.00"));
        second.setStatus(EbookStatus.ACTIVE);
        ebooks.saveAndFlush(second);
        assertThat(pay(id, 2, 2000)).doesNotContain("Ebook login created");
        JsonNode after = access(id);
        assertThat(after.path("userId").asText()).isEqualTo(userId);
        assertThat(after.path("ebooks")).hasSize(2);

        // Main Admin labels it as an Automation Admin student's ebook login.
        users.admin("main.admin.ebook@test.local");
        String admin = api.login("main.admin.ebook@test.local", TestUsers.PASSWORD);
        JsonNode row = api.data(api.get(admin, "/api/admin/students?search=kiran.mhatre")).path("content").get(0);
        assertThat(row.path("academyEbookOnly").asBoolean()).isTrue();
        assertThat(row.path("studentId").asText()).isEqualTo(studentId);
    }

    @Test
    void theOfficeSeesThePasswordUntilTheStudentChangesItAndCanResetOrEmailIt() throws Exception {
        emailWorks();
        long id = student("Laxmi Patil", "9876522202", "laxmi.patil@example.com").path("id").asLong();
        pay(id, 1, 3000);
        JsonNode a = access(id);
        String userId = a.path("userId").asText();
        String first = a.path("password").asText();
        api.post(office, "/api/automation/students/" + id + "/ebook-access/email", null).andExpect(status().isOk());

        // The student sets their own password: the office no longer sees one.
        String pupil = api.login(userId, first);
        api.post(pupil, "/api/auth/change-password", Map.of("currentPassword", first, "newPassword", "MyOwnPass9")).andExpect(status().isOk());
        JsonNode changed = access(id);
        assertThat(changed.path("password").isNull()).isTrue();
        assertThat(changed.path("passwordChanged").asBoolean()).isTrue();
        api.post(office, "/api/automation/students/" + id + "/ebook-access/email", null).andExpect(status().isBadRequest());

        // Reset password: a new one is shown and emailed; the old one stops working.
        JsonNode reset = api.data(api.post(office, "/api/automation/students/" + id + "/ebook-access/reset-password", null).andExpect(status().isOk()));
        String fresh = reset.path("password").asText();
        assertThat(fresh).hasSize(12).isNotEqualTo(first);
        api.post(null, "/api/auth/login", Map.of("identifier", userId, "password", "MyOwnPass9", "portal", "STUDENT")).andExpect(status().isUnauthorized());
        assertThat(api.login(userId, fresh)).isNotBlank();

        // Reset device: the registered device is cleared.
        api.post(office, "/api/automation/students/" + id + "/ebook-access/reset-device", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceName").doesNotExist());
    }

    @Test
    void blockingStopsTheEbookAndUnblockingGivesItBack() throws Exception {
        emailWorks();
        long id = student("Manoj Koli", "9876522203", "manoj.koli@example.com").path("id").asLong();
        // Blocked before any payment: the login is then made with the ebook blocked.
        api.patch(office, "/api/automation/students/" + id + "/ebook-access/blocked?blocked=true", Map.of()).andExpect(status().isOk());
        pay(id, 1, 3000);
        JsonNode a = access(id);
        assertThat(a.path("state").asText()).isEqualTo("BLOCKED");
        String pupil = api.login(a.path("userId").asText(), a.path("password").asText());
        assertThat(api.data(api.get(pupil, "/api/student/ebooks")).get(0).path("status").asText()).isEqualTo("REVOKED");
        api.get(pupil, "/api/student/ebooks/" + ebook.getId() + "/download").andExpect(status().isForbidden());
        User portal = students.findById(id).orElseThrow().getPortalUser();
        assertThatThrownBy(() -> entitlements.requireAccess(portal.getId(), ebook.getId())).isInstanceOf(ApiException.class);

        JsonNode open = api.data(api.patch(office, "/api/automation/students/" + id + "/ebook-access/blocked?blocked=false", Map.of())
                .andExpect(status().isOk()));
        assertThat(open.path("state").asText()).isEqualTo("ACTIVE");
        assertThat(api.data(api.get(pupil, "/api/student/ebooks")).get(0).path("status").asText()).isEqualTo("ACTIVE");
        assertThat(entitlements.requireAccess(portal.getId(), ebook.getId())).isNotNull();

        api.patch(office, "/api/automation/students/" + id + "/ebook-access/blocked?blocked=true", Map.of()).andExpect(status().isOk());
        assertThat(api.data(api.get(pupil, "/api/student/ebooks")).get(0).path("status").asText()).isEqualTo("REVOKED");
    }

    private static String statusOf(JsonNode academyEbooks, long ebookId) {
        for (JsonNode e : academyEbooks) {
            if (e.path("ebookId").asLong() == ebookId) {
                return e.path("status").asText();
            }
        }
        return null;
    }

    @Test
    void aNewEbookIsGivenToEveryAcademyLoginAtOnceBlockedForABlockedStudent() throws Exception {
        emailWorks();
        long open = student("Ganesh Patil", "9876522207", "ganesh.patil@example.com").path("id").asLong();
        long shut = student("Sunita Bhoir", "9876522208", "sunita.bhoir@example.com").path("id").asLong();
        pay(open, 1, 3000);
        pay(shut, 1, 3000);
        api.patch(office, "/api/automation/students/" + shut + "/ebook-access/blocked?blocked=true", Map.of()).andExpect(status().isOk());

        // The Main Admin puts a second ebook on sale: that is announced ...
        Ebook second = new Ebook();
        second.setEbookCode("EB-ACADEMY-2");
        second.setTitle("Option Trading Ebook");
        second.setPrice(new BigDecimal("599.00"));
        second.setPdfPath("ebooks/academy-2.pdf");
        second.setStatus(EbookStatus.INACTIVE);
        long secondId = ebooks.saveAndFlush(second).getId();
        ebookService.setStatus(secondId, EbookStatus.ACTIVE, users.admin("ebooks.admin@example.com"), "127.0.0.1");
        assertThat(events.stream(EbookActivatedEvent.class).filter(e -> e.ebookId() == secondId)).hasSize(1);

        // ... and once it is saved (the listener runs after the commit) every academy login gets it,
        // blocked for the blocked student. Running it again changes nothing.
        newEbookGrant.giveToAll(null, "127.0.0.1");
        newEbookGrant.giveToAll(null, "127.0.0.1");
        JsonNode a = access(open).path("ebooks"), b = access(shut).path("ebooks");
        assertThat(a).hasSize(2);
        assertThat(statusOf(a, secondId)).isEqualTo("ACTIVE");
        assertThat(statusOf(a, ebook.getId())).isEqualTo("ACTIVE");
        assertThat(b).hasSize(2);
        assertThat(statusOf(b, secondId)).isEqualTo("REVOKED");
        String pupil = api.login(access(open).path("userId").asText(), access(open).path("password").asText());
        assertThat(api.data(api.get(pupil, "/api/student/ebooks"))).hasSize(2);
    }

    @Test
    void aStudentWithoutAnEmailIsAskedForOneFirst() throws Exception {
        emailWorks();
        long id = student("Nisha Thakur", "9876522204", "nisha.thakur@example.com").path("id").asLong();
        AcadStudent s = students.findById(id).orElseThrow();
        s.setEmail(null);
        students.saveAndFlush(s);
        assertThat(pay(id, 1, 3000)).contains("No ebook login yet");
        assertThat(access(id).path("state").asText()).isEqualTo("NEEDS_EMAIL");
        api.post(office, "/api/automation/students/" + id + "/ebook-access", null).andExpect(status().isBadRequest());

        s = students.findById(id).orElseThrow();
        s.setEmail("nisha.thakur@example.com");
        students.saveAndFlush(s);
        assertThat(access(id).path("state").asText()).isEqualTo("READY");
        JsonNode made = api.data(api.post(office, "/api/automation/students/" + id + "/ebook-access", null).andExpect(status().isOk()));
        assertThat(made.path("state").asText()).isEqualTo("ACTIVE");
        api.post(office, "/api/automation/students/" + id + "/ebook-access", null).andExpect(status().isConflict());
    }

    @Test
    void anEmailThatAlreadyHasALoginIsHandledSafely() throws Exception {
        emailWorks();
        // An online student of the website: the ebook is added to their existing login.
        users.student("online.buyer@example.com");
        StudentProfile online = profiles.findAll().stream().filter(p -> p.getUser().getEmail().equals("online.buyer@example.com")).findFirst().orElseThrow();
        long id = student("Omkar Gharat", "9876522205", "online.buyer@example.com").path("id").asLong();
        pay(id, 1, 3000);
        JsonNode a = access(id);
        assertThat(a.path("linkedAccount").asBoolean()).isTrue();
        assertThat(a.path("password").isNull()).isTrue();
        assertThat(a.path("userId").asText()).isEqualTo(online.userId());
        assertThat(profiles.findById(online.getId()).orElseThrow().getStudentId()).isEqualTo(online.getStudentId());   // their website ID stays
        assertThat(a.path("ebooks")).hasSize(1);

        // A second academy student with the same email cannot share that login.
        long twin = student("Pooja Gharat", "9876522206", "online.buyer@example.com").path("id").asLong();
        assertThat(pay(twin, 1, 3000)).contains("The ebook login was not made").contains("already used by the ebook login of");
        api.get(office, "/api/automation/receipts?studentId=" + twin).andExpect(jsonPath("$.data.totalElements").value(1));   // the payment is kept

        // A staff email is never turned into a student login.
        users.admin("staff.member@example.com");
        long staff = student("Rahul Staff", "9876522207", "staff.member@example.com").path("id").asLong();
        assertThat(pay(staff, 1, 3000)).contains("belongs to a staff login");
        assertThat(access(staff).path("state").asText()).isEqualTo("READY");
    }

    @Test
    void academyAndWebsiteStudentIdsNeverClash() throws Exception {
        api.put(office, "/api/automation/settings/student-id-sequence", Map.of("nextStudentId", "LSI-B3-0001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("belong to the website")));
        api.put(office, "/api/automation/settings/student-id-sequence", Map.of("nextStudentId", "lsi-2026-00099"))
                .andExpect(status().isBadRequest());
        api.put(office, "/api/automation/settings/student-id-sequence", Map.of("nextStudentId", "LSA-B3-0001")).andExpect(status().isOk());
        assertThat(student("Sagar Naik", "9876522208", "sagar.naik@example.com").path("studentId").asText()).isEqualTo("LSA-B3-0001");

        // The startup repair of website Student IDs never touches an academy login (LSA/...).
        emailWorks();
        long id = student("Tanvi Kadam", "9876522209", "tanvi.kadam@example.com").path("id").asLong();
        pay(id, 1, 3000);
        StudentProfile p = profiles.findByUserId(students.findById(id).orElseThrow().getPortalUser().getId()).orElseThrow();
        String academyId = p.getStudentId();
        p.setLoginId(null);   // the only case the repair would otherwise act on
        profiles.saveAndFlush(p);
        studentIdRepair.repair();
        StudentProfile again = profiles.findById(p.getId()).orElseThrow();
        assertThat(again.getStudentId()).isEqualTo(academyId).startsWith("LSA");
        assertThat(again.isAcademyEbookOnly()).isTrue();
    }
}
