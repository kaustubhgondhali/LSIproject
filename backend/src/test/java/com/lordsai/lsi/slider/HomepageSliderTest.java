package com.lordsai.lsi.slider;

import com.fasterxml.jackson.databind.JsonNode;
import com.lordsai.lsi.support.ApiClient;
import com.lordsai.lsi.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mutual Fund homepage slider: seeded rows preserve the existing home.html slides, admin can
 * fully manage the list, only ACTIVE rows in display order reach the public endpoint, and the
 * feature is completely absent for the Share Market website (no admin surface, no data mixing).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HomepageSliderTest {

    @Autowired ApiClient api;
    @Autowired TestUsers users;
    @Autowired MockMvc mockMvc;

    private String admin, student;

    @BeforeEach
    void setUp() throws Exception {
        users.admin("admin@test.local");
        users.student("s@test.local");
        admin = api.login("admin@test.local", TestUsers.PASSWORD);
        student = api.login("s@test.local", TestUsers.PASSWORD);
    }

    private ResultActions create(String token, MockMultipartFile file, Map<String, String> fields) throws Exception {
        MockMultipartHttpServletRequestBuilder b = multipart("/api/admin/mutual-fund/slider").file(file);
        fields.forEach(b::param);
        b.header("Authorization", "Bearer " + token);
        return mockMvc.perform(b);
    }

    @Test
    void existingHardcodedSlidesWereMigratedActiveAndInOrder() throws Exception {
        JsonNode list = api.data(api.get(admin, "/api/admin/mutual-fund/slider").andExpect(status().isOk()));
        assertThat(list.size()).isEqualTo(5);
        assertThat(list.get(0).path("imagePath").asText()).isEqualTo("img/mf-hero-retirement.jpg");
        assertThat(list.get(0).path("active").asBoolean()).isTrue();
        assertThat(list.get(0).path("displayOrder").asInt()).isEqualTo(1);
        assertThat(list.get(4).path("imagePath").asText()).isEqualTo("img/mf-hero-sip.jpg");

        JsonNode pub = api.data(api.get(null, "/api/public/mutual-fund/slider-images").andExpect(status().isOk()));
        assertThat(pub.size()).isEqualTo(5);
        assertThat(pub.get(0).path("imagePath").asText()).isEqualTo("img/mf-hero-retirement.jpg");
        assertThat(pub.get(0).has("createdBy")).isFalse();
    }

    @Test
    void adminCanAddActivateDeactivateReplaceReorderAndDeleteASlide() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "banner.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2, 3});
        JsonNode created = api.data(create(admin, file, Map.of("title", "Diwali Offer", "altText", "Diwali SIP Offer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imagePath").value(org.hamcrest.Matchers.startsWith("images/")))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.title").value("Diwali Offer")));
        long id = created.path("id").asLong();

        // New slide is active by default -> appears publicly at the end (order 6).
        JsonNode pub = api.data(api.get(null, "/api/public/mutual-fund/slider-images"));
        assertThat(pub.size()).isEqualTo(6);
        assertThat(pub.get(5).path("id").asLong()).isEqualTo(id);
        assertThat(pub.get(5).path("title").asText()).isEqualTo("Diwali Offer");

        // Deactivate -> disappears publicly but stays in admin list.
        api.patch(admin, "/api/admin/mutual-fund/slider/" + id + "/active", Map.of("active", false)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
        assertThat(api.data(api.get(null, "/api/public/mutual-fund/slider-images")).size()).isEqualTo(5);
        assertThat(api.data(api.get(admin, "/api/admin/mutual-fund/slider")).size()).isEqualTo(6);

        // Reactivate.
        api.patch(admin, "/api/admin/mutual-fund/slider/" + id + "/active", Map.of("active", true)).andExpect(status().isOk());
        assertThat(api.data(api.get(null, "/api/public/mutual-fund/slider-images")).size()).isEqualTo(6);

        // Replace the image file.
        MockMultipartFile replacement = new MockMultipartFile("file", "banner2.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        String newPath = api.data(mockMvc.perform(multipart("/api/admin/mutual-fund/slider/" + id + "/image").file(replacement)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())).path("imagePath").asText();
        assertThat(newPath).startsWith("images/").endsWith(".png");

        // Reorder: move the new slide to the very front.
        JsonNode all = api.data(api.get(admin, "/api/admin/mutual-fund/slider"));
        List<Long> ids = new java.util.ArrayList<>();
        all.forEach(n -> ids.add(n.path("id").asLong()));
        ids.remove(Long.valueOf(id));
        ids.add(0, id);
        api.put(admin, "/api/admin/mutual-fund/slider/reorder", Map.of("orderedIds", ids)).andExpect(status().isOk());
        JsonNode reordered = api.data(api.get(null, "/api/public/mutual-fund/slider-images"));
        assertThat(reordered.get(0).path("id").asLong()).isEqualTo(id);

        // Delete it -> gone from both admin and public lists.
        api.delete(admin, "/api/admin/mutual-fund/slider/" + id).andExpect(status().isOk());
        assertThat(api.data(api.get(admin, "/api/admin/mutual-fund/slider")).size()).isEqualTo(5);
        assertThat(api.data(api.get(null, "/api/public/mutual-fund/slider-images")).size()).isEqualTo(5);

        JsonNode audit = api.data(api.get(admin, "/api/admin/audit-logs?entityType=HomepageSliderImage&entityId=" + id));
        assertThat(audit.path("content").toString()).contains(
                "SLIDER_IMAGE_CREATED", "SLIDER_IMAGE_DEACTIVATED", "SLIDER_IMAGE_ACTIVATED", "SLIDER_IMAGE_REPLACED", "SLIDER_IMAGE_DELETED");
    }

    @Test
    void createWithoutAnImageIsRejected() throws Exception {
        // No file part at all -> the required @RequestParam("file") itself triggers a 400.
        mockMvc.perform(multipart("/api/admin/mutual-fund/slider").header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyAdminCanManageTheSlider() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[]{1, 2, 3});
        api.get(null, "/api/admin/mutual-fund/slider").andExpect(status().isUnauthorized());
        api.get(student, "/api/admin/mutual-fund/slider").andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/admin/mutual-fund/slider").file(file).header("Authorization", "Bearer " + student))
                .andExpect(status().isForbidden());
        long anyId = api.data(api.get(admin, "/api/admin/mutual-fund/slider")).get(0).path("id").asLong();
        api.patch(student, "/api/admin/mutual-fund/slider/" + anyId + "/active", Map.of("active", false)).andExpect(status().isForbidden());
        api.delete(student, "/api/admin/mutual-fund/slider/" + anyId).andExpect(status().isForbidden());
        // Public endpoint stays open to everyone and unaffected.
        api.get(null, "/api/public/mutual-fund/slider-images").andExpect(status().isOk());
    }
}
