package com.lordsai.lsi.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Visitor review -> PENDING -> admin accept/decline -> public visibility. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReviewWorkflowTest {

    private static final String GOOD_TEXT = "The classroom sessions were practical and the risk management module changed how I trade.";

    @Autowired ApiClient api;
    @Autowired TestUsers users;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;

    private String admin, student;

    @BeforeEach
    void setUp() throws Exception {
        users.admin("admin@test.local");
        users.student("s@test.local");
        admin = api.login("admin@test.local", TestUsers.PASSWORD);
        student = api.login("s@test.local", TestUsers.PASSWORD);
    }

    private ResultActions submit(Map<String, String> fields, MockMultipartFile photo) throws Exception {
        MockMultipartHttpServletRequestBuilder b = multipart("/api/public/reviews");
        if (photo != null) {
            b.file(photo);
        }
        fields.forEach(b::param);
        return mockMvc.perform(b);
    }

    private Map<String, String> valid(String email, String site) {
        return Map.of("site", site, "fullName", "Rahul Sharma", "email", email, "course", "Swing Trading",
                "rating", "5", "reviewText", GOOD_TEXT);
    }

    private long idOf(ResultActions r) throws Exception {
        JsonNode n = mapper.readTree(r.andReturn().getResponse().getContentAsString());
        return n.path("data").path("id").asLong();
    }

    @Test
    void submittedReviewIsPendingHiddenUntilApprovedAndHiddenAgainWhenDeclined() throws Exception {
        MockMultipartFile photo = new MockMultipartFile("photo", "me.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2});
        ResultActions r = submit(valid("rahul@example.com", "ACADEMY"), photo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("published after approval")))
                .andExpect(jsonPath("$.data.email").doesNotExist());
        long id = idOf(r);

        // Test 3: not public yet
        api.get(null, "/api/public/reviews?site=ACADEMY").andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));

        // Test 4: admin sees it as PENDING, dashboard counts it
        api.get(admin, "/api/admin/reviews?status=PENDING").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(id))
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.content[0].email").value("rahul@example.com"))
                .andExpect(jsonPath("$.data.content[0].profileImagePath").value(org.hamcrest.Matchers.startsWith("images/")));
        api.get(admin, "/api/admin/dashboard").andExpect(jsonPath("$.data.pendingReviews").value(1))
                .andExpect(jsonPath("$.data.approvedReviews").value(0));

        // Test 5 + 6: approve -> visible publicly, without private fields
        api.patch(admin, "/api/admin/reviews/" + id + "/approve", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.approvedBy").value("Admin User"));
        api.get(null, "/api/public/reviews?site=ACADEMY").andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].fullName").value("Rahul Sharma"))
                .andExpect(jsonPath("$.data[0].rating").value(5))
                .andExpect(jsonPath("$.data[0].email").doesNotExist())
                .andExpect(jsonPath("$.data[0].submittedIp").doesNotExist());
        // The other website does not show it.
        api.get(null, "/api/public/reviews?site=MUTUAL_FUND").andExpect(jsonPath("$.data.length()").value(0));

        // Test 7: decline -> hidden again but kept
        api.patch(admin, "/api/admin/reviews/" + id + "/decline", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DECLINED"));
        api.get(null, "/api/public/reviews?site=ACADEMY").andExpect(jsonPath("$.data.length()").value(0));
        api.get(admin, "/api/admin/reviews/" + id).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DECLINED"));
        // ...and a declined review can be approved later.
        api.patch(admin, "/api/admin/reviews/" + id + "/approve", null).andExpect(status().isOk());
        api.get(null, "/api/public/reviews?site=ACADEMY").andExpect(jsonPath("$.data.length()").value(1));

        JsonNode audit = api.data(api.get(admin, "/api/admin/audit-logs?entityType=Review&entityId=" + id));
        assertThat(audit.path("content").toString()).contains("REVIEW_SUBMITTED", "REVIEW_APPROVED", "REVIEW_DECLINED");
    }

    @Test
    void mutualFundWebsiteHasItsOwnReviews() throws Exception {
        long id = idOf(submit(valid("mf@example.com", "MUTUAL_FUND"), null).andExpect(status().isOk()));
        api.patch(admin, "/api/admin/reviews/" + id + "/approve", null).andExpect(status().isOk());
        api.get(null, "/api/public/reviews?site=MUTUAL_FUND").andExpect(jsonPath("$.data.length()").value(1));
        api.get(null, "/api/public/reviews?site=ACADEMY").andExpect(jsonPath("$.data.length()").value(0));
        api.get(null, "/api/public/reviews?site=SHARE_MARKET").andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void shareMarketWebsiteSubmissionAndCrossWebsiteIsolation() throws Exception {
        // Review A: Mutual Fund
        long idA = idOf(submit(valid("reviewA@example.com", "MUTUAL_FUND"), null).andExpect(status().isOk()));
        // Review B: Share Market (using SHARE_MARKET site code)
        long idB = idOf(submit(valid("reviewB@example.com", "SHARE_MARKET"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("awaiting approval"))));

        // Admin sees both as PENDING
        api.get(admin, "/api/admin/reviews?status=PENDING&site=MUTUAL_FUND")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(idA));
        api.get(admin, "/api/admin/reviews?status=PENDING&site=SHARE_MARKET")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(idB));

        // Approve both
        api.patch(admin, "/api/admin/reviews/" + idA + "/approve", null).andExpect(status().isOk());
        api.patch(admin, "/api/admin/reviews/" + idB + "/approve", null).andExpect(status().isOk());

        // Verify Cross-Website Isolation:
        // Mutual Fund shows Review A, NOT Review B
        api.get(null, "/api/public/reviews?site=MUTUAL_FUND")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(idA));

        // Share Market shows Review B, NOT Review A (queried via SHARE_MARKET or ACADEMY)
        api.get(null, "/api/public/reviews?site=SHARE_MARKET")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(idB));

        api.get(null, "/api/public/reviews?site=ACADEMY")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(idB));
    }

    // Test 9: validation
    @Test
    void invalidSubmissionsAreRejected() throws Exception {
        Map<String, String> base = valid("v@example.com", "ACADEMY");
        java.util.function.BiFunction<String, String, Map<String, String>> with = (k, v) -> {
            Map<String, String> m = new java.util.HashMap<>(base); m.put(k, v); return m;
        };
        submit(with.apply("fullName", "  "), null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.fullName").exists());
        submit(with.apply("email", "not-an-email"), null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.email").exists());
        submit(with.apply("rating", "0"), null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.rating").exists());
        submit(with.apply("rating", "6"), null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.rating").exists());
        submit(with.apply("reviewText", "Too short"), null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.reviewText").exists());
        submit(with.apply("reviewText", "x".repeat(1001)), null).andExpect(status().isBadRequest());
        submit(with.apply("reviewText", "Great course, visit http://spam.example for more details and more text"), null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Links are not allowed in reviews."));
        // Wrong photo type
        submit(base, new MockMultipartFile("photo", "x.exe", "application/octet-stream", new byte[8])).andExpect(status().isBadRequest());
        // Nothing got stored.
        api.get(admin, "/api/admin/reviews").andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void htmlIsStrippedAndDuplicatesAreBlocked() throws Exception {
        Map<String, String> m = new java.util.HashMap<>(valid("dup@example.com", "ACADEMY"));
        m.put("fullName", "<b>Bold</b> Name<script>alert(1)</script>");
        m.put("reviewText", "<script>alert('x')</script>" + GOOD_TEXT + " <img src=x onerror=alert(1)>");
        long id = idOf(submit(m, null).andExpect(status().isOk()));
        JsonNode stored = api.data(api.get(admin, "/api/admin/reviews/" + id));
        assertThat(stored.path("fullName").asText()).isEqualTo("Bold Namealert(1)");
        assertThat(stored.path("reviewText").asText()).doesNotContain("<", ">");

        // Second pending review from the same email on the same website is refused.
        submit(valid("dup@example.com", "ACADEMY"), null).andExpect(status().isConflict());
    }

    // ---- Admin Panel testimonials: add / edit / photo / delete ---------------------------------

    @Test
    void adminCanAddEditAndDeleteTestimonials() throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("site", "SHARE_MARKET"); body.put("fullName", "Job Professional — Uran"); body.put("course", "Swing Trading & Risk Management");
        body.put("rating", 5); body.put("reviewText", GOOD_TEXT); // no email, default status -> APPROVED

        JsonNode created = api.data(api.post(admin, "/api/admin/reviews", body).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.approvedBy").value("Admin User")));
        long id = created.path("id").asLong();

        // Visible immediately on the Share Market page only.
        api.get(null, "/api/public/reviews?site=SHARE_MARKET").andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].fullName").value("Job Professional — Uran"));
        api.get(null, "/api/public/reviews?site=MUTUAL_FUND").andExpect(jsonPath("$.data.length()").value(0));

        // Edit: change text, rating, move to Mutual Fund and hide it.
        body.put("reviewText", "Updated: the SIP planning sessions were clear, practical and well structured for beginners.");
        body.put("rating", 4); body.put("site", "MUTUAL_FUND"); body.put("status", "PENDING");
        api.put(admin, "/api/admin/reviews/" + id, body).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").value(4))
                .andExpect(jsonPath("$.data.site").value("MUTUAL_FUND"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.reviewText").value(org.hamcrest.Matchers.startsWith("Updated:")));
        api.get(null, "/api/public/reviews?site=SHARE_MARKET").andExpect(jsonPath("$.data.length()").value(0));
        api.get(null, "/api/public/reviews?site=MUTUAL_FUND").andExpect(jsonPath("$.data.length()").value(0));
        // Approve again through the existing moderation endpoint -> public on Mutual Fund; no email to notify, no error.
        api.patch(admin, "/api/admin/reviews/" + id + "/approve", null).andExpect(status().isOk());
        api.get(null, "/api/public/reviews?site=MUTUAL_FUND").andExpect(jsonPath("$.data.length()").value(1));

        // Photo upload / replace / remove through the admin endpoints.
        MockMultipartFile photo = new MockMultipartFile("file", "p.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, 1});
        String path1 = api.data(mockMvc.perform(multipart("/api/admin/reviews/" + id + "/photo").file(photo).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())).path("profileImagePath").asText();
        assertThat(path1).startsWith("images/");
        String path2 = api.data(mockMvc.perform(multipart("/api/admin/reviews/" + id + "/photo").file(photo).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())).path("profileImagePath").asText();
        assertThat(path2).isNotEqualTo(path1);
        api.delete(admin, "/api/admin/reviews/" + id + "/photo").andExpect(status().isOk()).andExpect(jsonPath("$.data.profileImagePath").doesNotExist());

        // Validation on create/update.
        Map<String, Object> bad = new java.util.HashMap<>(body); bad.put("fullName", ""); bad.put("rating", 9); bad.put("reviewText", "short");
        api.post(admin, "/api/admin/reviews", bad).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fullName").exists()).andExpect(jsonPath("$.errors.rating").exists()).andExpect(jsonPath("$.errors.reviewText").exists());
        api.put(admin, "/api/admin/reviews/999999", body).andExpect(status().isNotFound());

        // Delete removes it everywhere.
        api.delete(admin, "/api/admin/reviews/" + id).andExpect(status().isOk());
        api.get(admin, "/api/admin/reviews/" + id).andExpect(status().isNotFound());
        api.get(null, "/api/public/reviews?site=MUTUAL_FUND").andExpect(jsonPath("$.data.length()").value(0));
        JsonNode audit = api.data(api.get(admin, "/api/admin/audit-logs?entityType=Review&entityId=" + id));
        assertThat(audit.path("content").toString()).contains("REVIEW_CREATED_BY_ADMIN", "REVIEW_UPDATED", "REVIEW_PHOTO_UPLOADED", "REVIEW_PHOTO_REMOVED", "REVIEW_DELETED");
    }

    @Test
    void studentsAndAnonymousCannotUseAdminTestimonialCrud() throws Exception {
        Map<String, Object> body = Map.of("site", "SHARE_MARKET", "fullName", "X Y", "rating", 5, "reviewText", GOOD_TEXT);
        api.post(student, "/api/admin/reviews", body).andExpect(status().isForbidden());
        api.post(null, "/api/admin/reviews", body).andExpect(status().isUnauthorized());
        long id = api.data(api.post(admin, "/api/admin/reviews", body)).path("id").asLong();
        api.put(student, "/api/admin/reviews/" + id, body).andExpect(status().isForbidden());
        api.delete(student, "/api/admin/reviews/" + id + "/photo").andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/admin/reviews/" + id + "/photo").file(new MockMultipartFile("file", "p.jpg", "image/jpeg", new byte[]{1}))
                .header("Authorization", "Bearer " + student)).andExpect(status().isForbidden());
        api.delete(student, "/api/admin/reviews/" + id).andExpect(status().isForbidden());
        api.get(admin, "/api/admin/reviews/" + id).andExpect(status().isOk());
    }

    // Test 8: RBAC
    @Test
    void onlyAdminCanModerate() throws Exception {
        long id = idOf(submit(valid("r@example.com", "ACADEMY"), null).andExpect(status().isOk()));
        api.get(null, "/api/admin/reviews").andExpect(status().isUnauthorized());
        api.patch(null, "/api/admin/reviews/" + id + "/approve", null).andExpect(status().isUnauthorized());
        api.get(student, "/api/admin/reviews").andExpect(status().isForbidden());
        api.patch(student, "/api/admin/reviews/" + id + "/approve", null).andExpect(status().isForbidden());
        api.patch(student, "/api/admin/reviews/" + id + "/decline", null).andExpect(status().isForbidden());
        api.delete(student, "/api/admin/reviews/" + id).andExpect(status().isForbidden());
        // Still pending and still hidden.
        api.get(null, "/api/public/reviews?site=ACADEMY").andExpect(jsonPath("$.data.length()").value(0));
        api.delete(admin, "/api/admin/reviews/" + id).andExpect(status().isOk());
        api.get(admin, "/api/admin/reviews/" + id).andExpect(status().isNotFound());
    }
}
