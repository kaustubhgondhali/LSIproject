package com.lordsai.lsi.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.lordsai.lsi.automation.entity.AcadStudent;
import com.lordsai.lsi.automation.repository.AcadStudentRepository;
import com.lordsai.lsi.support.ApiClient;
import com.lordsai.lsi.support.TestAadhaar;
import com.lordsai.lsi.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Year;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Automation Admin > Student Admission: the full admission form is saved, read back, edited, and old records keep working. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudentAdmissionTest {

    @Autowired ApiClient api;
    @Autowired TestUsers users;
    @Autowired AcadStudentRepository students;

    private String auto;
    private long batch1, course1;

    @BeforeEach
    void setUp() throws Exception {
        auto = api.login("automation", "Automation@123");
        JsonNode l = api.data(api.get(auto, "/api/automation/lookups"));
        batch1 = l.path("batches").get(0).path("id").asLong();
        course1 = l.path("courses").get(0).path("id").asLong();
    }

    /** The fields a new admission must have (email and address are compulsory when adding). */
    private Map<String, Object> core(String name, String mobile) {
        Map<String, Object> m = new HashMap<>();
        m.put("fullName", name); m.put("batchId", batch1); m.put("courseId", course1);
        m.put("admissionDate", "2026-09-01"); m.put("mobile", mobile);
        m.put("email", "student" + mobile + "@example.com"); m.put("address", "Uran, Navi Mumbai");
        m.put("aadhaarNo", TestAadhaar.next());
        return m;
    }

    private static final String SERIES = "/api/automation/settings/student-id-sequence";

    private String create(Map<String, Object> body) throws Exception {
        return api.data(api.post(auto, "/api/automation/students", body).andExpect(status().isOk())).path("studentId").asText();
    }

    // ---- Address and E-mail ID are compulsory when adding -------------------------------------------

    @Test
    void addingAStudentRequiresEmailAndAddressButOlderRecordsStillEdit() throws Exception {
        Map<String, Object> noEmail = core("No Email", "9876500010");
        noEmail.remove("email");
        api.post(auto, "/api/automation/students", noEmail).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("E-mail ID is required."));
        Map<String, Object> blankAddress = core("No Address", "9876500011");
        blankAddress.put("address", "   ");
        api.post(auto, "/api/automation/students", blankAddress).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Address is required."));
        Map<String, Object> badEmail = core("Bad Email", "9876500012");
        badEmail.put("email", "not-an-email");
        api.post(auto, "/api/automation/students", badEmail).andExpect(status().isBadRequest());

        // A record from before this rule (or imported) with neither still opens, edits and saves.
        long id = api.data(api.post(auto, "/api/automation/students", core("Legacy Record", "9876500013"))).path("id").asLong();
        AcadStudent legacy = students.findById(id).orElseThrow();
        legacy.setEmail(null);
        legacy.setAddress(null);
        students.save(legacy);
        Map<String, Object> edit = core("Legacy Record Renamed", "9876500013");
        edit.remove("email");
        edit.remove("address");
        api.put(auto, "/api/automation/students/" + id, edit).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Legacy Record Renamed"));
    }

    // ---- the office decides the Student ID series -----------------------------------------------------

    @Test
    void theOfficeCanSetTheStudentIdSerialAndYearAlwaysFollowsTheCalendar() throws Exception {
        int year = api.data(api.get(auto, SERIES)).path("year").asInt();
        String before = create(core("Default Series", "9876500020"));
        assertThat(before).isEqualTo("LSA/" + year + "/0001");

        // Website format and custom academy formats are refused — only LSA/{current year}/{serial}.
        api.put(auto, SERIES, Map.of("nextStudentId", "lsi-b3-0100")).andExpect(status().isBadRequest());
        api.put(auto, SERIES, Map.of("nextStudentId", "LSA-B3-0100")).andExpect(status().isBadRequest());
        api.put(auto, SERIES, Map.of("nextStudentId", "LSA/" + (year - 1) + "/0100")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("current year")));

        api.put(auto, SERIES, Map.of("nextStudentId", "LSA/" + year + "/0100")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextStudentId").value("LSA/" + year + "/0100"));
        String first = create(core("Series One", "9876500021"));
        String second = create(core("Series Two", "9876500022"));
        assertThat(first).isEqualTo("LSA/" + year + "/0100");
        assertThat(second).isEqualTo("LSA/" + year + "/0101");
        api.get(auto, SERIES).andExpect(jsonPath("$.data.nextStudentId").value("LSA/" + year + "/0102"))
                .andExpect(jsonPath("$.data.highestExistingStudentId").value("LSA/" + year + "/0101"));

        api.put(auto, SERIES, Map.of("nextStudentId", "LSA/" + year + "/0101")).andExpect(status().isConflict());
        api.put(auto, SERIES, Map.of("nextNumber", 500)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextStudentId").value("LSA/" + year + "/0500"));
        assertThat(create(core("Series Three", "9876500023"))).isEqualTo("LSA/" + year + "/0500");

        for (String bad : new String[]{"LSA/" + year + "/", "LSA-B3-0001", "ABC%12", "X-0"}) {
            api.put(auto, SERIES, Map.of("nextStudentId", bad)).andExpect(status().isBadRequest());
        }

        api.put(auto, SERIES, Map.of("nextStudentId", "LSA/" + year + "/0001")).andExpect(status().isConflict());
        // Must be above the highest issued serial (0500); jumping backward is refused.
        api.put(auto, SERIES, Map.of("nextStudentId", "LSA/" + year + "/0600")).andExpect(status().isOk());
        assertThat(create(core("Back To Default", "9876500024"))).isEqualTo("LSA/" + year + "/0600");

        api.get(auto, "/api/automation/search?q=Series One").andExpect(jsonPath("$.data[0].studentId").value("LSA/" + year + "/0100"));
        api.get(auto, "/api/automation/search?q=Default Series").andExpect(jsonPath("$.data[0].studentId").value(before));
    }

    private Map<String, Object> fullAdmission() {
        Map<String, Object> m = core("Priya Deshmukh", "9876500001");
        m.put("email", "priya@example.com");
        m.put("address", "12 MG Road, Panvel");
        m.put("dateOfBirth", "1998-04-15");
        m.put("gender", "FEMALE");
        m.put("highestQualification", "B.Com");
        m.put("instituteName", "Mumbai University");
        m.put("yearOfCompletion", 2019);
        m.put("employmentStatus", "EMPLOYED");
        m.put("currentEmployer", "Tata Consultancy Services");
        m.put("jobTitle", "Analyst");
        m.put("annualIncome", 650000.50);
        m.put("stockMarketExperience", true);
        m.put("stockMarketYears", 2.5);
        m.put("tradingExperience", false);
        m.put("tradingYears", 3);            // ignored: the answer was "No"
        m.put("tradingPlatforms", "Zerodha Kite");
        m.put("tradingStrategies", "Swing trading");
        m.put("preferredStartDate", "2026-10-05");
        m.put("paymentMethod", "BANK_TRANSFER");
        return m;
    }

    @Test
    void theCompleteAdmissionFormIsStoredAndReadBackEverywhere() throws Exception {
        JsonNode created = api.data(api.post(auto, "/api/automation/students", fullAdmission()).andExpect(status().isOk()));
        long id = created.path("id").asLong();
        assertThat(created.path("studentId").asText()).matches("LSA/\\d{4}/\\d{4}");

        for (String path : new String[]{"/api/automation/students/" + id, "/api/automation/students/" + id + "/profile"}) {
            JsonNode s = api.data(api.get(auto, path).andExpect(status().isOk()));
            if (s.has("student")) s = s.path("student");
            JsonNode a = s.path("admission");
            assertThat(s.path("fullName").asText()).isEqualTo("Priya Deshmukh");
            assertThat(s.path("admissionDate").asText()).isEqualTo("2026-09-01");
            assertThat(s.path("address").asText()).isEqualTo("12 MG Road, Panvel");
            assertThat(s.path("mobile").asText()).isEqualTo("9876500001");
            assertThat(s.path("email").asText()).isEqualTo("priya@example.com");
            assertThat(s.path("courseId").asLong()).isEqualTo(course1);
            assertThat(a.path("dateOfBirth").asText()).isEqualTo("1998-04-15");
            assertThat(a.path("gender").asText()).isEqualTo("FEMALE");
            assertThat(a.path("highestQualification").asText()).isEqualTo("B.Com");
            assertThat(a.path("instituteName").asText()).isEqualTo("Mumbai University");
            assertThat(a.path("yearOfCompletion").asInt()).isEqualTo(2019);
            assertThat(a.path("employmentStatus").asText()).isEqualTo("EMPLOYED");
            assertThat(a.path("currentEmployer").asText()).isEqualTo("Tata Consultancy Services");
            assertThat(a.path("jobTitle").asText()).isEqualTo("Analyst");
            assertThat(a.path("annualIncome").decimalValue()).isEqualByComparingTo("650000.50");
            assertThat(a.path("stockMarketExperience").asBoolean()).isTrue();
            assertThat(a.path("stockMarketYears").decimalValue()).isEqualByComparingTo("2.5");
            assertThat(a.path("tradingExperience").asBoolean()).isFalse();
            assertThat(a.path("tradingYears").isNull()).isTrue();
            assertThat(a.path("tradingPlatforms").asText()).isEqualTo("Zerodha Kite");
            assertThat(a.path("tradingStrategies").asText()).isEqualTo("Swing trading");
            assertThat(a.path("preferredStartDate").asText()).isEqualTo("2026-10-05");
            assertThat(a.path("paymentMethod").asText()).isEqualTo("BANK_TRANSFER");
        }
        // The list still works and carries the same details.
        api.get(auto, "/api/automation/students?q=priya").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].admission.paymentMethod").value("BANK_TRANSFER"));

        // Edit several fields; untouched ones keep their values, cleared ones become null.
        Map<String, Object> edit = fullAdmission();
        edit.put("gender", "OTHER");
        edit.put("employmentStatus", "SELF_EMPLOYED");
        edit.put("currentEmployer", "Own consultancy");
        edit.remove("jobTitle");
        edit.put("annualIncome", 900000);
        edit.put("stockMarketExperience", false);
        edit.put("tradingExperience", true);
        edit.put("tradingYears", 1.5);
        edit.put("paymentMethod", "CASH");
        edit.put("preferredStartDate", "2026-11-02");
        api.put(auto, "/api/automation/students/" + id, edit).andExpect(status().isOk());

        JsonNode a = api.data(api.get(auto, "/api/automation/students/" + id)).path("admission");
        assertThat(a.path("gender").asText()).isEqualTo("OTHER");
        assertThat(a.path("employmentStatus").asText()).isEqualTo("SELF_EMPLOYED");
        assertThat(a.path("currentEmployer").asText()).isEqualTo("Own consultancy");
        assertThat(a.path("jobTitle").isNull()).isTrue();
        assertThat(a.path("annualIncome").decimalValue()).isEqualByComparingTo("900000");
        assertThat(a.path("stockMarketExperience").asBoolean()).isFalse();
        assertThat(a.path("stockMarketYears").isNull()).isTrue();
        assertThat(a.path("tradingYears").decimalValue()).isEqualByComparingTo("1.5");
        assertThat(a.path("paymentMethod").asText()).isEqualTo("CASH");
        assertThat(a.path("preferredStartDate").asText()).isEqualTo("2026-11-02");
        assertThat(a.path("instituteName").asText()).isEqualTo("Mumbai University");
    }

    @Test
    void recordsWithoutAdmissionDetailsStillCreateEditAndOpen() throws Exception {
        // Exactly what callers sent before this change (and what imported records look like).
        long id = api.data(api.post(auto, "/api/automation/students", core("Old Style", "9876500002")).andExpect(status().isOk())).path("id").asLong();
        JsonNode a = api.data(api.get(auto, "/api/automation/students/" + id)).path("admission");
        assertThat(a.path("dateOfBirth").isNull()).isTrue();
        assertThat(a.path("paymentMethod").isNull()).isTrue();
        assertThat(a.path("stockMarketExperience").isNull()).isTrue();

        Map<String, Object> edit = core("Old Style Renamed", "9876500002");
        edit.put("email", "");
        edit.put("gender", "");          // blank selects are accepted and stored as "not given"
        edit.put("paymentMethod", "");
        api.put(auto, "/api/automation/students/" + id, edit).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Old Style Renamed"))
                .andExpect(jsonPath("$.data.admission.gender").value(org.hamcrest.Matchers.nullValue()));
        api.get(auto, "/api/automation/students/" + id + "/profile").andExpect(status().isOk());
    }

    @Test
    void invalidAdmissionValuesAreRejected() throws Exception {
        Object[][] bad = {
                {"fullName", "Rahul 123"},
                {"email", "not-an-email"},
                {"mobile", "12345"},
                {"dateOfBirth", LocalDate.now().plusDays(1).toString()},
                {"dateOfBirth", "1850-01-01"},
                {"gender", "UNKNOWN"},
                {"yearOfCompletion", 1900},
                {"yearOfCompletion", Year.now().getValue() + 11},
                {"employmentStatus", "RETIRED"},
                {"annualIncome", -1},
                {"annualIncome", 12.345},
                {"stockMarketYears", 61},
                {"paymentMethod", "UPI"},
        };
        for (Object[] b : bad) {
            Map<String, Object> m = fullAdmission();
            m.put((String) b[0], b[1]);
            api.post(auto, "/api/automation/students", m).andExpect(status().isBadRequest());
        }
        // "(if applicable)" fields and the rest of the new fields are never forced.
        Map<String, Object> minimal = core("Minimal Admission", "9876500003");
        minimal.put("employmentStatus", "STUDENT");
        api.post(auto, "/api/automation/students", minimal).andExpect(status().isOk());
    }

    @Test
    void admissionDataStaysBehindTheAutomationAdminLogin() throws Exception {
        long id = api.data(api.post(auto, "/api/automation/students", fullAdmission())).path("id").asLong();
        users.student("pupil@test.local");
        String student = api.login("pupil@test.local", TestUsers.PASSWORD);
        api.get(student, "/api/automation/students/" + id).andExpect(status().isForbidden());
        api.get(null, "/api/automation/students/" + id).andExpect(status().isUnauthorized());
        api.post(student, "/api/automation/students", fullAdmission()).andExpect(status().isForbidden());
    }
}
