package com.lordsai.lsi.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lordsai.lsi.entity.StudentDevice;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.DeviceStatus;
import com.lordsai.lsi.entity.enums.Role;
import com.lordsai.lsi.repository.StudentDeviceRepository;
import com.lordsai.lsi.repository.UserRepository;
import com.lordsai.lsi.repository.UserSessionRepository;
import com.lordsai.lsi.service.DeviceBindingService;
import com.lordsai.lsi.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.test.context.TestPropertySource(properties = "lsi.security.device-binding-enforced=true")
@Transactional
class DeviceBindingTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TestUsers testUsers;
    @Autowired UserRepository userRepository;
    @Autowired StudentDeviceRepository studentDeviceRepository;
    @Autowired UserSessionRepository userSessionRepository;
    @Autowired DeviceBindingService deviceBindingService;

    private User student;
    private User admin;

    @BeforeEach
    void setUp() {
        student = testUsers.student("devbound@example.com");
        admin = testUsers.admin("admin-device@example.com");
    }

    @Test
    void studentFirstLogin_requiresDeviceRegistration_andCompletesSuccessfully() throws Exception {
        // Step 1: First login triggers DEVICE_REGISTRATION_REQUIRED
        String loginBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", "devbound@example.com", "password", TestUsers.PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deviceStatus").value("DEVICE_REGISTRATION_REQUIRED"))
                .andExpect(jsonPath("$.data.tempToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        JsonNode loginData = objectMapper.readTree(loginBody).path("data");
        String tempToken = loginData.path("tempToken").asText();

        String otp = deviceBindingService.getLatestOtpForTesting(student.getId());
        assertThat(otp).isNotNull().hasSize(6);

        // Step 2: Complete registration with OTP
        String regBody = mockMvc.perform(post("/api/auth/device/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "tempToken", tempToken,
                                "otp", otp,
                                "deviceName", "Workstation Laptop",
                                "devicePlatform", "Windows 11"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.deviceId").isNotEmpty())
                .andExpect(jsonPath("$.data.deviceToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        JsonNode regData = objectMapper.readTree(regBody).path("data");
        String deviceId = regData.path("deviceId").asText();
        String deviceToken = regData.path("deviceToken").asText();
        String accessToken = regData.path("accessToken").asText();

        // Verify StudentDevice record in DB
        StudentDevice bound = studentDeviceRepository.findByUserId(student.getId()).orElse(null);
        assertThat(bound).isNotNull();
        assertThat(bound.getDeviceId()).isEqualTo(deviceId);
        assertThat(bound.getDeviceName()).isEqualTo("Workstation Laptop");
        assertThat(bound.getDeviceStatus()).isEqualTo(DeviceStatus.ACTIVE);

        // Step 3: Subsequent login with registered device credentials succeeds directly
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "identifier", "devbound@example.com",
                                "password", TestUsers.PASSWORD,
                                "deviceId", deviceId,
                                "deviceToken", deviceToken
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.deviceId").value(deviceId));

        // Step 4: Access protected student endpoint with device headers succeeds
        mockMvc.perform(get("/api/student/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Device-Id", deviceId)
                        .header("X-Device-Token", deviceToken))
                .andExpect(status().isOk());

        // Step 5: Stolen JWT or unauthorized device without matching device headers is REJECTED
        mockMvc.perform(get("/api/student/profile")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/student/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Device-Id", "fake-device")
                        .header("X-Device-Token", "fake-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void secondaryBrowser_onRegisteredComputer_linksBrowserWithoutCreatingSecondDevice() throws Exception {
        // Register initial device first
        StudentDevice initial = new StudentDevice();
        initial.setUser(student);
        initial.setDeviceId("dev_registered_123");
        initial.setDeviceSecretHash(com.lordsai.lsi.util.TokenUtil.sha256Hex("secret_abc"));
        initial.setDeviceName("Student MacBook");
        initial.setDevicePlatform("MacIntel");
        initial.setDeviceStatus(DeviceStatus.ACTIVE);
        initial.setRegisteredAt(java.time.Instant.now());
        initial.setLastSeenAt(java.time.Instant.now());
        studentDeviceRepository.saveAndFlush(initial);

        // Login without device credentials (e.g. student opened Edge / Firefox on same laptop)
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", "devbound@example.com", "password", TestUsers.PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceStatus").value("DEVICE_LINK_REQUIRED"))
                .andExpect(jsonPath("$.data.registeredDeviceName").value("Student MacBook"))
                .andReturn().getResponse().getContentAsString();

        String tempToken = objectMapper.readTree(body).path("data").path("tempToken").asText();
        String otp = deviceBindingService.getLatestOtpForTesting(student.getId());
        assertThat(otp).isNotNull();

        // Complete browser link
        mockMvc.perform(post("/api/auth/device/link-browser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "tempToken", tempToken,
                                "otp", otp,
                                "devicePlatform", "MacIntel"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.deviceId").value("dev_registered_123"))
                .andExpect(jsonPath("$.data.deviceToken").isNotEmpty());

        // Verify only 1 device record still exists
        long count = studentDeviceRepository.findAll().stream().filter(d -> d.getUser().getId().equals(student.getId())).count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void studentSelfServiceReset_invalidatesOldDevice_andEnablesNewDevice() throws Exception {
        // Initial registered device
        StudentDevice initial = new StudentDevice();
        initial.setUser(student);
        initial.setDeviceId("dev_old_laptop");
        initial.setDeviceSecretHash(com.lordsai.lsi.util.TokenUtil.sha256Hex("old_secret"));
        initial.setDeviceName("Old Broken Laptop");
        initial.setDevicePlatform("Windows");
        initial.setDeviceStatus(DeviceStatus.ACTIVE);
        initial.setRegisteredAt(java.time.Instant.now());
        initial.setLastSeenAt(java.time.Instant.now());
        studentDeviceRepository.saveAndFlush(initial);

        // Step 1: Request reset
        String resetReqBody = mockMvc.perform(post("/api/auth/device/reset-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", "devbound@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();

        String tempToken = objectMapper.readTree(resetReqBody).path("data").path("tempToken").asText();
        String otp = deviceBindingService.getLatestOtpForTesting(student.getId());
        assertThat(otp).isNotNull();

        // Step 2: Confirm reset
        mockMvc.perform(post("/api/auth/device/reset-confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "tempToken", tempToken,
                                "otp", otp
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify old device is deleted and sessions are revoked
        assertThat(studentDeviceRepository.findByUserId(student.getId())).isEmpty();
        assertThat(userSessionRepository.findByUserIdAndActiveTrue(student.getId())).isEmpty();

        // Student's next login requires registering the new device
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", "devbound@example.com", "password", TestUsers.PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceStatus").value("DEVICE_REGISTRATION_REQUIRED"));
    }

    @Test
    void adminCanResetStudentDevice() throws Exception {
        // Register device
        StudentDevice dev = new StudentDevice();
        dev.setUser(student);
        dev.setDeviceId("dev_to_be_reset");
        dev.setDeviceSecretHash(com.lordsai.lsi.util.TokenUtil.sha256Hex("secret123"));
        dev.setDeviceName("Stolen Laptop");
        dev.setDevicePlatform("Windows");
        dev.setDeviceStatus(DeviceStatus.ACTIVE);
        dev.setRegisteredAt(java.time.Instant.now());
        dev.setLastSeenAt(java.time.Instant.now());
        studentDeviceRepository.saveAndFlush(dev);

        // Admin logs in
        String adminLoginBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", "admin-device@example.com", "password", TestUsers.PASSWORD, "portal", "ADMIN"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String adminToken = objectMapper.readTree(adminLoginBody).path("data").path("accessToken").asText();

        // Admin queries student device
        mockMvc.perform(get("/api/admin/students/" + student.getId() + "/device")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceName").value("Stolen Laptop"));

        // Admin resets device
        mockMvc.perform(post("/api/admin/students/" + student.getId() + "/reset-device")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Device is marked reset required or removed
        StudentDevice refreshed = studentDeviceRepository.findByUserId(student.getId()).orElse(null);
        assertThat(refreshed == null || refreshed.isResetRequired()).isTrue();
    }

    @Test
    void adminAccount_doesNotRequireDeviceBinding() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", "admin-device@example.com", "password", TestUsers.PASSWORD, "portal", "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.deviceStatus").value("AUTHENTICATED"));
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }
}

