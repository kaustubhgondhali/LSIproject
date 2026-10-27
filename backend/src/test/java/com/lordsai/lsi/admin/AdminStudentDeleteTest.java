package com.lordsai.lsi.admin;

import com.lordsai.lsi.entity.Ebook;
import com.lordsai.lsi.entity.enums.EbookStatus;
import com.lordsai.lsi.repository.EbookRepository;
import com.lordsai.lsi.repository.StudentProfileRepository;
import com.lordsai.lsi.repository.UserRepository;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Main Admin > Students > Delete: a student added by mistake (who still has an unused password
 * setup link) is really deleted; a student with other records gets a clear "deactivate instead"
 * answer rather than a server error.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminStudentDeleteTest {

    @Autowired ApiClient api;
    @Autowired TestUsers users;
    @Autowired UserRepository userRepository;
    @Autowired StudentProfileRepository profiles;
    @Autowired EbookRepository ebooks;

    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        users.admin("delete.admin@test.local");
        admin = api.login("delete.admin@test.local", TestUsers.PASSWORD);
    }

    private long addStudent(String email) throws Exception {
        return api.data(api.post(admin, "/api/admin/students",
                Map.of("fullName", "Added By Mistake", "email", email, "mobile", "9876543210"))
                .andExpect(status().isOk())).path("id").asLong();
    }

    @Test
    void aStudentAddedByMistakeIsDeletedWithTheirUnusedSetupLink() throws Exception {
        long id = addStudent("mistake@example.com");

        api.delete(admin, "/api/admin/students/" + id).andExpect(status().isOk());

        assertThat(userRepository.findById(id)).isEmpty();
        assertThat(profiles.findByUserId(id)).isEmpty();
    }

    @Test
    void aStudentWithOtherRecordsIsToldToDeactivateInstead() throws Exception {
        long id = addStudent("withebook@example.com");
        Ebook ebook = new Ebook();
        ebook.setEbookCode("EB-DELETE");
        ebook.setTitle("Delete Check Ebook");
        ebook.setPrice(new BigDecimal("199.00"));
        ebook.setPdfPath("ebooks/delete.pdf");
        ebook.setStatus(EbookStatus.ACTIVE);
        long ebookId = ebooks.saveAndFlush(ebook).getId();
        api.post(admin, "/api/admin/ebook-entitlements", Map.of("studentUserId", id, "ebookId", ebookId)).andExpect(status().isOk());

        api.delete(admin, "/api/admin/students/" + id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Deactivate it instead")));
    }
}
