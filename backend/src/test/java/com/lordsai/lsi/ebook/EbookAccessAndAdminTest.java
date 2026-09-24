package com.lordsai.lsi.ebook;

import com.fasterxml.jackson.databind.JsonNode;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.repository.AuditLogRepository;
import com.lordsai.lsi.repository.EbookRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin ebook management (upload, replace PDF, price, activate / deactivate, public listing) and
 * the protected ebook download: no login -> 401, no entitlement -> 403 (audited), owner -> PDF.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EbookAccessAndAdminTest {

    private static final byte[] PDF = "%PDF-1.4\n%test\n".getBytes();
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    @Autowired ApiClient api;
    @Autowired TestUsers users;
    @Autowired MockMvc mockMvc;
    @Autowired EbookRepository ebookRepository;
    @Autowired AuditLogRepository auditLogRepository;

    private String admin, studentA, studentB;
    private User userA, userB;
    private long ebookId;

    @BeforeEach
    void setUp() throws Exception {
        users.admin("admin@test.local");
        userA = users.student("a@test.local");
        userB = users.student("b@test.local");
        admin = api.login("admin@test.local", TestUsers.PASSWORD);
        studentA = api.login("a@test.local", TestUsers.PASSWORD);
        studentB = api.login("b@test.local", TestUsers.PASSWORD);

        JsonNode created = api.data(api.post(admin, "/api/admin/ebooks", Map.of(
                "ebookCode", "eb-101", "title", "Candlestick Workbook", "author", "Vaibhav Pawar",
                "price", 1299, "discountedPrice", 799, "category", "Technical Analysis", "language", "English")));
        ebookId = created.path("id").asLong();
        assertThat(created.path("status").asText()).isEqualTo("DRAFT");
        assertThat(created.path("ebookCode").asText()).isEqualTo("EB-101");
    }

    @Test
    void uploadPdfActivateAndListPublicly() throws Exception {
        // Not purchasable / not activatable before a PDF exists.
        api.patch(admin, "/api/admin/ebooks/" + ebookId + "/status", Map.of("status", "ACTIVE")).andExpect(status().isBadRequest());

        mockMvc.perform(multipart("/api/admin/ebooks/" + ebookId + "/pdf")
                        .file(new MockMultipartFile("file", "../../evil name?.pdf", "application/pdf", PDF))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasPdf").value(true))
                .andExpect(jsonPath("$.data.pdfOriginalName").value("evil name_.pdf"));
        // Wrong type is rejected.
        mockMvc.perform(multipart("/api/admin/ebooks/" + ebookId + "/pdf")
                        .file(new MockMultipartFile("file", "virus.exe", "application/octet-stream", new byte[16]))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/admin/ebooks/" + ebookId + "/cover")
                        .file(new MockMultipartFile("file", "cover.png", "image/png", PNG))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverImagePath").value(org.hamcrest.Matchers.startsWith("images/")));

        mockMvc.perform(get("/api/public/ebooks")).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        api.patch(admin, "/api/admin/ebooks/" + ebookId + "/status", Map.of("status", "ACTIVE")).andExpect(status().isOk());
        mockMvc.perform(get("/api/public/ebooks")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Candlestick Workbook"))
                .andExpect(jsonPath("$.data[0].effectivePrice").value(799))
                .andExpect(jsonPath("$.data[0].pdfPath").doesNotExist());

        // Replace the PDF: the stored path changes and the old file is gone.
        String before = ebookRepository.findById(ebookId).orElseThrow().getPdfPath();
        mockMvc.perform(multipart("/api/admin/ebooks/" + ebookId + "/pdf")
                        .file(new MockMultipartFile("file", "v2.pdf", "application/pdf", PDF))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        assertThat(ebookRepository.findById(ebookId).orElseThrow().getPdfPath()).isNotEqualTo(before);
        assertThat(auditLogRepository.findAll().stream().map(a -> a.getAction()).toList())
                .contains("EBOOK_CREATED", "EBOOK_PDF_UPLOADED", "EBOOK_PDF_REPLACED", "EBOOK_ACTIVATED", "EBOOK_COVER_UPLOADED");

        api.patch(admin, "/api/admin/ebooks/" + ebookId + "/status", Map.of("status", "INACTIVE")).andExpect(status().isOk());
        mockMvc.perform(get("/api/public/ebooks")).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void adminChangesEbookPriceAndItIsAudited() throws Exception {
        api.patch(admin, "/api/admin/ebooks/" + ebookId + "/price", Map.of("price", 1999, "discountedPrice", 1499))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.effectivePrice").value(1499));
        api.patch(admin, "/api/admin/ebooks/" + ebookId + "/price", Map.of("price", 100, "discountedPrice", 200))
                .andExpect(status().isBadRequest());
        assertThat(auditLogRepository.findAll().stream().anyMatch(a -> "EBOOK_PRICE_CHANGED".equals(a.getAction()))).isTrue();
        // Students / anonymous cannot manage ebooks.
        api.patch(studentA, "/api/admin/ebooks/" + ebookId + "/price", Map.of("price", 1)).andExpect(status().isForbidden());
    }

    @Test
    void protectedDownloadRequiresLoginAndEntitlement() throws Exception {
        mockMvc.perform(multipart("/api/admin/ebooks/" + ebookId + "/pdf")
                .file(new MockMultipartFile("file", "book.pdf", "application/pdf", PDF))
                .header("Authorization", "Bearer " + admin)).andExpect(status().isOk());

        // TEST 5: file URL without login.
        mockMvc.perform(get("/api/student/ebooks/" + ebookId + "/download")).andExpect(status().isUnauthorized());
        // No entitlement yet -> 403 and an audit row.
        api.get(studentA, "/api/student/ebooks/" + ebookId + "/download").andExpect(status().isForbidden());
        assertThat(auditLogRepository.findAll().stream().anyMatch(a -> a.getAction().startsWith("PROTECTION_")
                && a.getDescription().contains("ebook " + ebookId))).isTrue();

        // Admin grants A access (manual entitlement) -> A can read, B cannot (TEST 6).
        api.post(admin, "/api/admin/ebook-entitlements", Map.of("studentUserId", userA.getId(), "ebookId", ebookId))
                .andExpect(status().isOk());
        api.post(admin, "/api/admin/ebook-entitlements", Map.of("studentUserId", userA.getId(), "ebookId", ebookId))
                .andExpect(status().isConflict());
        api.get(studentA, "/api/student/ebooks").andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].ebookId").value(ebookId))
                .andExpect(jsonPath("$.data[0].fileAvailable").value(true));
        byte[] body = api.get(studentA, "/api/student/ebooks/" + ebookId + "/download")
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", "application/pdf"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "private, no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(body).isEqualTo(PDF);

        byte[] viewBody = api.get(studentA, "/api/student/ebooks/" + ebookId + "/view")
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "private, no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(viewBody).isEqualTo(PDF);

        api.get(studentB, "/api/student/ebooks/" + ebookId + "/download").andExpect(status().isForbidden());
        api.get(studentB, "/api/student/ebooks").andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));

        // Revoked entitlement stops access.
        long entitlementId = api.data(api.get(admin, "/api/admin/students/" + userA.getId() + "/ebooks")).get(0).path("id").asLong();
        api.patch(admin, "/api/admin/ebook-entitlements/" + entitlementId + "/status?status=REVOKED", null).andExpect(status().isOk());
        api.get(studentA, "/api/student/ebooks/" + ebookId + "/download").andExpect(status().isForbidden());
    }

    @Test
    void purchasedEbookCannotBeDeletedOnlyDeactivated() throws Exception {
        api.post(admin, "/api/admin/ebook-entitlements", Map.of("studentUserId", userB.getId(), "ebookId", ebookId)).andExpect(status().isOk());
        api.delete(admin, "/api/admin/ebooks/" + ebookId).andExpect(status().isConflict());
        api.get(admin, "/api/admin/ebooks/" + ebookId).andExpect(jsonPath("$.data.purchaseCount").value(1));
    }

    @Test
    void unpurchasedEbookCanBeDeleted() throws Exception {
        api.delete(admin, "/api/admin/ebooks/" + ebookId).andExpect(status().isOk());
        assertThat(ebookRepository.findById(ebookId)).isEmpty();
    }
}
