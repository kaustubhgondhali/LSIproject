package com.lordsai.lsi.communication;

import com.lordsai.lsi.support.ApiClient;
import com.lordsai.lsi.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Main Admin > WhatsApp Settings > Website WhatsApp number, and the public read the website uses. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WebsiteWhatsAppNumberTest {

    @Autowired ApiClient api;
    @Autowired TestUsers users;

    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        users.admin("admin@test.local");
        admin = api.login("admin@test.local", TestUsers.PASSWORD);
    }

    @Test
    void defaultsTo9987049070AndCanBeChangedByTheMainAdmin() throws Exception {
        api.get(null, "/api/public/site-contact").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.whatsappNumber").value("919987049070"));
        api.get(admin, "/api/admin/whatsapp/website-number").andExpect(jsonPath("$.data.number").value("919987049070"));

        api.put(admin, "/api/admin/whatsapp/website-number", Map.of("number", "98200 12345")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.number").value("919820012345"));
        api.get(null, "/api/public/site-contact").andExpect(jsonPath("$.data.whatsappNumber").value("919820012345"));

        api.put(admin, "/api/admin/whatsapp/website-number", Map.of("number", "+91 99870 49070")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.number").value("919987049070"));
    }

    @Test
    void rejectsInvalidNumbersAndNonAdmins() throws Exception {
        for (String bad : new String[]{"12345", "5987049070", "abc"}) {
            api.put(admin, "/api/admin/whatsapp/website-number", Map.of("number", bad)).andExpect(status().isBadRequest());
        }
        api.put(null, "/api/admin/whatsapp/website-number", Map.of("number", "9987049070")).andExpect(status().isUnauthorized());
        String student = api.login(users.student("stud@test.local").getEmail(), TestUsers.PASSWORD);
        api.put(student, "/api/admin/whatsapp/website-number", Map.of("number", "9987049070")).andExpect(status().isForbidden());
        api.get(null, "/api/public/site-contact").andExpect(jsonPath("$.data.whatsappNumber").value("919987049070"));
    }
}
