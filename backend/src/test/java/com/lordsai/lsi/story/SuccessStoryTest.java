package com.lordsai.lsi.story;

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
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SuccessStoryTest {

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

    private Map<String, Object> story(String category, String title) {
        Map<String, Object> m = new HashMap<>();
        m.put("category", category);
        m.put("title", title);
        m.put("personName", "Person");
        m.put("location", "Uran");
        m.put("description", "A story");
        return m;
    }

    @Test
    void seededPlaceholderStoriesArePreservedButNotPublished() throws Exception {
        // V7 seeded six stories carrying a demo placeholder video id; V11 unpublishes them so an
        // unrelated video is never shown as a real student story. Rows stay for the admin to fix.
        api.get(null, "/api/public/stories").andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        JsonNode all = api.data(api.get(admin, "/api/admin/stories").andExpect(status().isOk()));
        assertThat(all.size()).isEqualTo(6);
        assertThat(all.get(0).path("category").asText()).isEqualTo("STUDENTS");
        assertThat(all.get(0).path("published").asBoolean()).isFalse();
        assertThat(all.get(0).path("description").asText()).contains("Placeholder");
        api.get(null, "/api/public/stories?category=TEACHERS").andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        api.get(null, "/api/public/stories?category=PARENTS").andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void storyAppearsPubliclyOnlyAfterPublishingAndVideoIsRequiredToPublish() throws Exception {
        long id = api.data(api.post(admin, "/api/admin/stories", story("TEACHERS", "Faculty story")).andExpect(status().isOk()))
                .path("id").asLong();
        api.get(null, "/api/public/stories?category=TEACHERS").andExpect(jsonPath("$.data.length()").value(0));

        // No video yet -> cannot publish.
        api.patch(admin, "/api/admin/stories/" + id + "/publish", Map.of("published", true)).andExpect(status().isBadRequest());
        // Unpublished uploads are never streamable.
        mockMvc.perform(get("/api/public/stories/" + id + "/video")).andExpect(status().isNotFound());

        MockMultipartFile video = new MockMultipartFile("file", "story.mp4", "video/mp4", new byte[128]);
        mockMvc.perform(multipart("/api/admin/stories/" + id + "/video").file(video).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.hasUploadedVideo").value(true));
        MockMultipartFile thumb = new MockMultipartFile("file", "t.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8});
        mockMvc.perform(multipart("/api/admin/stories/" + id + "/thumbnail").file(thumb).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.thumbnailPath").isString());

        api.patch(admin, "/api/admin/stories/" + id + "/publish", Map.of("published", true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.published").value(true));

        JsonNode teachers = api.data(api.get(null, "/api/public/stories?category=TEACHERS"));
        assertThat(teachers.size()).isEqualTo(1);
        assertThat(teachers.get(0).path("videoType").asText()).isEqualTo("UPLOAD");
        assertThat(teachers.get(0).path("thumbnailPath").asText()).startsWith("images/");
        mockMvc.perform(get("/api/public/stories/" + id + "/video")).andExpect(status().isPartialContent());
        mockMvc.perform(get("/api/public/stories/" + id + "/video").header("Range", "bytes=0-9")).andExpect(status().isPartialContent());

        // Unpublish -> hidden and not streamable again.
        api.patch(admin, "/api/admin/stories/" + id + "/publish", Map.of("published", false)).andExpect(status().isOk());
        api.get(null, "/api/public/stories?category=TEACHERS").andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/public/stories/" + id + "/video")).andExpect(status().isNotFound());

        // Removing the video is audited and the story is deletable.
        api.delete(admin, "/api/admin/stories/" + id + "/video").andExpect(status().isOk()).andExpect(jsonPath("$.data.hasUploadedVideo").value(false));
        api.delete(admin, "/api/admin/stories/" + id).andExpect(status().isOk());
        api.get(admin, "/api/admin/stories/" + id).andExpect(status().isNotFound());
        JsonNode audit = api.data(api.get(admin, "/api/admin/audit-logs?entityType=SuccessStory&entityId=" + id));
        assertThat(audit.path("content").toString()).contains("STORY_CREATED", "STORY_VIDEO_UPLOADED", "STORY_PUBLISHED", "STORY_DELETED");
    }

    @Test
    void embedUrlIsValidatedAndReorderWorks() throws Exception {
        Map<String, Object> bad = story("PARENTS", "Bad link");
        bad.put("videoUrl", "https://evil.example/embed/x");
        api.post(admin, "/api/admin/stories", bad).andExpect(status().isBadRequest());

        Map<String, Object> good = story("PARENTS", "Parent story");
        good.put("videoUrl", "https://www.youtube.com/embed/abc123_XYZ");
        long id = api.data(api.post(admin, "/api/admin/stories", good)).path("id").asLong();
        api.patch(admin, "/api/admin/stories/" + id + "/publish", Map.of("published", true)).andExpect(status().isOk());
        api.get(null, "/api/public/stories?category=PARENTS").andExpect(jsonPath("$.data[0].videoUrl").value("https://www.youtube.com/embed/abc123_XYZ"));

        JsonNode list = api.data(api.get(admin, "/api/admin/stories"));
        long first = list.get(0).path("id").asLong();
        api.put(admin, "/api/admin/stories/reorder", Map.of("orderedIds", List.of(id, first))).andExpect(status().isOk());
        assertThat(api.data(api.get(null, "/api/public/stories")).get(0).path("id").asLong()).isEqualTo(id);
    }

    @Test
    void onlyAdminManagesStories() throws Exception {
        api.get(student, "/api/admin/stories").andExpect(status().isForbidden());
        api.post(student, "/api/admin/stories", story("STUDENTS", "x")).andExpect(status().isForbidden());
        api.get(null, "/api/admin/stories").andExpect(status().isUnauthorized());
        api.delete(student, "/api/admin/stories/1").andExpect(status().isForbidden());
    }
}
