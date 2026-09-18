package com.lordsai.lsi.ebook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lordsai.lsi.entity.Ebook;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.repository.EbookRepository;
import com.lordsai.lsi.service.FileStorageService;
import com.lordsai.lsi.support.ApiClient;
import com.lordsai.lsi.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The simplified ebook flow: ONE admin request with name + price + cover + PDF (+ optional info)
 * creates a live ebook that the store lists immediately; files are content-checked, edits keep
 * files unless replaced, and the PDF stays protected exactly as before.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EbookSimpleUploadTest {

    private static final byte[] PDF = "%PDF-1.4\n%test ebook\n".getBytes();
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2};

    @Autowired ApiClient api;
    @Autowired TestUsers users;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired EbookRepository ebookRepository;
    @Autowired FileStorageService storage;

    private String admin, student;
    private User studentUser;

    @BeforeEach
    void setUp() throws Exception {
        users.admin("admin@test.local");
        studentUser = users.student("reader@test.local");
        admin = api.login("admin@test.local", TestUsers.PASSWORD);
        student = api.login("reader@test.local", TestUsers.PASSWORD);
    }

    private ResultActions upload(String token, String method, String path, Map<String, String> fields,
                                 MockMultipartFile cover, MockMultipartFile pdf) throws Exception {
        MockMultipartHttpServletRequestBuilder b = multipart(HttpMethod.valueOf(method), path);
        if (cover != null) b.file(cover);
        if (pdf != null) b.file(pdf);
        for (Map.Entry<String, String> f : fields.entrySet()) b.param(f.getKey(), f.getValue());
        if (token != null) b.header("Authorization", "Bearer " + token);
        return mockMvc.perform(b);
    }

    private static MockMultipartFile cover(String name, String type, byte[] bytes) { return new MockMultipartFile("cover", name, type, bytes); }
    private static MockMultipartFile pdf(String name, String type, byte[] bytes) { return new MockMultipartFile("pdf", name, type, bytes); }

    private JsonNode data(ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString()).path("data");
    }

    // ---- the exact scenario from the specification -------------------------------------------------

    @Test
    void adminUploadsInOneStepAndTheStoreShowsItImmediately() throws Exception {
        ResultActions r = upload(admin, "POST", "/api/admin/ebooks",
                Map.of("title", "  Test Stock Market Ebook ", "price", "499", "description", "Test ebook description"),
                cover("my cover photo.JPG", "image/jpeg", JPG), pdf("stock-market-basics-final-v3.pdf", "application/pdf", PDF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Ebook uploaded successfully and is now available in the Store."))
                .andExpect(jsonPath("$.data.title").value("Test Stock Market Ebook"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.hasPdf").value(true))
                .andExpect(jsonPath("$.data.effectivePrice").value(499))
                .andExpect(jsonPath("$.data.coverImagePath").value(org.hamcrest.Matchers.startsWith("images/")))
                .andExpect(jsonPath("$.data.pdfOriginalName").value("stock-market-basics-final-v3.pdf"));
        long id = data(r).path("id").asLong();

        // Cover and PDF are separate files in separate folders; the PDF is never under the public images folder.
        Ebook e = ebookRepository.findById(id).orElseThrow();
        assertThat(e.getEbookCode()).isEqualTo("TEST-STOCK-MARKET-EBOOK");
        assertThat(e.getCoverImagePath()).startsWith("images/").endsWith(".jpg");
        assertThat(e.getPdfPath()).startsWith("ebooks/").endsWith(".pdf");
        assertThat(storage.exists(e.getCoverImagePath())).isTrue();
        assertThat(storage.exists(e.getPdfPath())).isTrue();
        assertThat(storage.readBytes(e.getPdfPath())).isEqualTo(PDF);

        // Store page data: title (not the filename), price, info, cover; nothing hard-coded.
        mockMvc.perform(get("/api/public/ebooks")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(id))
                .andExpect(jsonPath("$.data[0].title").value("Test Stock Market Ebook"))
                .andExpect(jsonPath("$.data[0].effectivePrice").value(499))
                .andExpect(jsonPath("$.data[0].shortDescription").value("Test ebook description"))
                .andExpect(jsonPath("$.data[0].coverImagePath").value(e.getCoverImagePath()))
                .andExpect(jsonPath("$.data[0].hasPdf").value(true))
                .andExpect(jsonPath("$.data[0].pdfPath").doesNotExist());
        // The cover is publicly viewable; the PDF is not reachable through the public images endpoint.
        mockMvc.perform(get("/api/public/" + e.getCoverImagePath())).andExpect(status().isOk());
        mockMvc.perform(get("/api/public/images/" + e.getPdfPath().substring("ebooks/".length()))).andExpect(status().isNotFound());

        // Protected access is unchanged: login + entitlement required.
        mockMvc.perform(get("/api/student/ebooks/" + id + "/download")).andExpect(status().isUnauthorized());
        api.get(student, "/api/student/ebooks/" + id + "/download").andExpect(status().isForbidden());
        api.post(admin, "/api/admin/ebook-entitlements", Map.of("studentUserId", studentUser.getId(), "ebookId", id)).andExpect(status().isOk());
        api.get(student, "/api/student/ebooks/" + id + "/download").andExpect(status().isOk());
    }

    // ---- validation -----------------------------------------------------------------------------------

    @Test
    void rejectsMissingNameOrPriceAndNothingIsStored() throws Exception {
        upload(admin, "POST", "/api/admin/ebooks", Map.of("title", "   ", "price", "10"), cover("c.png", "image/png", PNG), pdf("b.pdf", "application/pdf", PDF))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Ebook name is required."));
        upload(admin, "POST", "/api/admin/ebooks", Map.of("title", "No price"), cover("c.png", "image/png", PNG), pdf("b.pdf", "application/pdf", PDF))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Price is required."));
        upload(admin, "POST", "/api/admin/ebooks", Map.of("title", "Negative", "price", "-5"), cover("c.png", "image/png", PNG), pdf("b.pdf", "application/pdf", PDF))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Price cannot be negative."));
        upload(admin, "POST", "/api/admin/ebooks", Map.of("title", "Text price", "price", "abc"), cover("c.png", "image/png", PNG), pdf("b.pdf", "application/pdf", PDF))
                .andExpect(status().isBadRequest());
        assertThat(ebookRepository.count()).isZero();
    }

    @Test
    void onlyRealPdfsAndRealImagesAreAcceptedWithFriendlyMessages() throws Exception {
        // Missing / wrong extension / renamed file / wrong declared type — all "Please upload a valid PDF Ebook."
        upload(admin, "POST", "/api/admin/ebooks", Map.of("title", "A", "price", "1"), cover("c.png", "image/png", PNG), null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Please upload a valid PDF Ebook."));
        upload(admin, "POST", "/api/admin/ebooks", Map.of("title", "A", "price", "1"), cover("c.png", "image/png", PNG), pdf("notes.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", PDF))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Please upload a valid PDF Ebook."));
        upload(admin, "POST", "/api/admin/ebooks", Map.of("title", "A", "price", "1"), cover("c.png", "image/png", PNG), pdf("renamed.pdf", "application/pdf", "MZ this is an exe".getBytes()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Please upload a valid PDF Ebook."));
        upload(admin, "POST", "/api/admin/ebooks", Map.of("title", "A", "price", "1"), cover("c.png", "image/png", PNG), pdf("book.pdf", "application/octet-stream", PDF))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Please upload a valid PDF Ebook."));
        // Cover: missing / exe renamed to .png / PDF as cover
        upload(admin, "POST", "/api/admin/ebooks", Map.of("title", "A", "price", "1"), null, pdf("book.pdf", "application/pdf", PDF))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Please upload a valid cover image."));
        upload(admin, "POST", "/api/admin/ebooks", Map.of("title", "A", "price", "1"), cover("virus.png", "image/png", "MZ".getBytes()), pdf("book.pdf", "application/pdf", PDF))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Please upload a valid cover image."));
        upload(admin, "POST", "/api/admin/ebooks", Map.of("title", "A", "price", "1"), cover("book.pdf", "application/pdf", PDF), pdf("book.pdf", "application/pdf", PDF))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Please upload a valid cover image."));
        assertThat(ebookRepository.count()).isZero();
        // WebP cover is fine.
        byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes();
        upload(admin, "POST", "/api/admin/ebooks", Map.of("title", "WebP cover", "price", "1"), cover("c.webp", "image/webp", webp), pdf("book.pdf", "application/pdf", PDF))
                .andExpect(status().isOk());
    }

    @Test
    void duplicateNamesAreRefusedSoDoubleSubmitsCannotCreateTwoEbooks() throws Exception {
        upload(admin, "POST", "/api/admin/ebooks", Map.of("title", "Price Action Guide", "price", "299"), cover("c.png", "image/png", PNG), pdf("b.pdf", "application/pdf", PDF))
                .andExpect(status().isOk());
        upload(admin, "POST", "/api/admin/ebooks", Map.of("title", "price action guide", "price", "299"), cover("c.png", "image/png", PNG), pdf("b.pdf", "application/pdf", PDF))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("An ebook with this name already exists."));
        assertThat(ebookRepository.count()).isEqualTo(1);
    }

    // ---- editing --------------------------------------------------------------------------------------

    @Test
    void editKeepsExistingFilesUnlessReplaced() throws Exception {
        long id = data(upload(admin, "POST", "/api/admin/ebooks", Map.of("title", "Old Name", "price", "100", "description", "old"),
                cover("c.png", "image/png", PNG), pdf("v1.pdf", "application/pdf", PDF))).path("id").asLong();
        Ebook before = ebookRepository.findById(id).orElseThrow();
        String oldCover = before.getCoverImagePath(), oldPdf = before.getPdfPath();

        // Title + price only: both files stay.
        upload(admin, "PUT", "/api/admin/ebooks/" + id, Map.of("title", "New Name", "price", "150", "description", ""), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("New Name"))
                .andExpect(jsonPath("$.data.effectivePrice").value(150))
                .andExpect(jsonPath("$.data.hasPdf").value(true));
        Ebook mid = ebookRepository.findById(id).orElseThrow();
        assertThat(mid.getCoverImagePath()).isEqualTo(oldCover);
        assertThat(mid.getPdfPath()).isEqualTo(oldPdf);
        assertThat(mid.getShortDescription()).isNull();
        assertThat(mid.getStatus().name()).isEqualTo("ACTIVE");

        // New PDF only: PDF replaced (old file removed), cover kept; existing buyers read the new file.
        byte[] v2 = "%PDF-1.7\n%v2\n".getBytes();
        upload(admin, "PUT", "/api/admin/ebooks/" + id, Map.of("title", "New Name", "price", "150"), null, pdf("v2.pdf", "application/pdf", v2))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.pdfOriginalName").value("v2.pdf"));
        Ebook after = ebookRepository.findById(id).orElseThrow();
        assertThat(after.getCoverImagePath()).isEqualTo(oldCover);
        assertThat(after.getPdfPath()).isNotEqualTo(oldPdf);
        assertThat(storage.exists(oldPdf)).isFalse();
        assertThat(storage.readBytes(after.getPdfPath())).isEqualTo(v2);

        // A bad replacement is refused and changes nothing.
        upload(admin, "PUT", "/api/admin/ebooks/" + id, Map.of("title", "New Name", "price", "150"), cover("x.png", "image/png", "nope".getBytes()), null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Please upload a valid cover image."));
        assertThat(ebookRepository.findById(id).orElseThrow().getCoverImagePath()).isEqualTo(oldCover);
    }

    // ---- security -------------------------------------------------------------------------------------

    @Test
    void onlyTheMainAdminCanUpload() throws Exception {
        upload(student, "POST", "/api/admin/ebooks", Map.of("title", "X", "price", "1"), cover("c.png", "image/png", PNG), pdf("b.pdf", "application/pdf", PDF))
                .andExpect(status().isForbidden());
        upload(null, "POST", "/api/admin/ebooks", Map.of("title", "X", "price", "1"), cover("c.png", "image/png", PNG), pdf("b.pdf", "application/pdf", PDF))
                .andExpect(status().isUnauthorized());
        assertThat(ebookRepository.count()).isZero();
    }
}
