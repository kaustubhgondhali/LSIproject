package com.lordsai.lsi.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lordsai.lsi.repository.StudentDeviceRepository;
import com.lordsai.lsi.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The browser keeps ONE device binding (localStorage "lsi_device_binding") and sends it with every
 * login. A second student logging in on the same browser must still get in (device binding not
 * enforced): the first student's device id may not be reused, the second student gets their own.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SharedBrowserLoginTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TestUsers users;
    @Autowired StudentDeviceRepository deviceRepository;

    private JsonNode login(String identifier, String deviceId, String deviceToken) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("identifier", identifier);
        body.put("password", TestUsers.PASSWORD);
        body.put("portal", "STUDENT");
        if (deviceId != null) {
            body.put("deviceId", deviceId);
            body.put("deviceToken", deviceToken);
        }
        String json = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).path("data");
    }

    @Test
    void secondStudentOnTheSameBrowserCanLogIn() throws Exception {
        var first = users.student("first-on-browser@test.local");
        var second = users.student("second-on-browser@test.local");

        // First student: the browser has no binding yet; the server issues one and the browser stores it.
        JsonNode a = login("first-on-browser@test.local", null, null);
        String browserDeviceId = a.path("deviceId").asText();
        String browserDeviceToken = a.path("deviceToken").asText();
        assertThat(browserDeviceId).isNotBlank();

        // Second student on the SAME browser: it sends the first student's stored binding.
        JsonNode b = login("second-on-browser@test.local", browserDeviceId, browserDeviceToken);
        String secondDeviceId = b.path("deviceId").asText();
        assertThat(secondDeviceId).isNotBlank().isNotEqualTo(browserDeviceId);
        assertThat(b.path("deviceToken").asText()).isNotBlank();

        assertThat(deviceRepository.findByUserId(first.getId()).orElseThrow().getDeviceId()).isEqualTo(browserDeviceId);
        assertThat(deviceRepository.findByUserId(second.getId()).orElseThrow().getDeviceId()).isEqualTo(secondDeviceId);

        // Both keep working afterwards, whichever binding the browser holds now.
        login("first-on-browser@test.local", secondDeviceId, b.path("deviceToken").asText());
        login("second-on-browser@test.local", secondDeviceId, b.path("deviceToken").asText());
    }
}
