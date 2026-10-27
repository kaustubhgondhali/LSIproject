package com.lordsai.lsi.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.repository.UserRepository;
import com.lordsai.lsi.service.DeviceBindingService;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Small helper so tests read like API calls: login once, then call with the token. */
@Component
@Lazy
public class ApiClient {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final DeviceBindingService deviceBindingService;
    private final UserRepository userRepository;
    private final com.lordsai.lsi.repository.StudentProfileRepository studentProfileRepository;

    public record DeviceCredentials(String deviceId, String deviceToken) {}

    private final Map<String, DeviceCredentials> tokenDeviceMap = new ConcurrentHashMap<>();
    private final Map<String, DeviceCredentials> userDeviceMap = new ConcurrentHashMap<>();

    public ApiClient(MockMvc mockMvc, ObjectMapper objectMapper,
                     DeviceBindingService deviceBindingService,
                     UserRepository userRepository,
                     com.lordsai.lsi.repository.StudentProfileRepository studentProfileRepository) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.deviceBindingService = deviceBindingService;
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
    }

    private User findUser(String identifier) {
        if (identifier == null || identifier.isBlank()) return null;
        if (identifier.contains("@")) {
            return userRepository.findByEmailIgnoreCase(identifier.trim().toLowerCase()).orElse(null);
        }
        var fromProfile = studentProfileRepository.findByStudentIdIgnoreCase(identifier.trim());
        if (fromProfile.isPresent()) return fromProfile.get().getUser();
        var matches = userRepository.findByUsername(identifier.trim().toLowerCase());
        return matches.size() == 1 ? matches.get(0) : null;
    }

    public void clearDeviceCredentials() {
        tokenDeviceMap.clear();
        userDeviceMap.clear();
    }

    public void registerDeviceCredential(String token, String deviceId, String deviceToken) {
        tokenDeviceMap.put(token, new DeviceCredentials(deviceId, deviceToken));
    }

    public String login(String identifier, String password) throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("identifier", identifier);
        req.put("password", password);
        DeviceCredentials saved = userDeviceMap.get(identifier);
        if (saved != null) {
            req.put("deviceId", saved.deviceId());
            req.put("deviceToken", saved.deviceToken());
        }

        String body = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(body);
        if (!node.path("success").asBoolean()) {
            throw new AssertionError("Login failed: " + body);
        }

        JsonNode data = node.path("data");
        String deviceStatus = data.path("deviceStatus").asText(null);

        if ("DEVICE_REGISTRATION_REQUIRED".equals(deviceStatus)) {
            String tempToken = data.path("tempToken").asText();
            User user = findUser(identifier);
            if (user == null) {
                throw new AssertionError("User not found for test login: " + identifier);
            }
            String otp = deviceBindingService.getLatestOtpForTesting(user.getId());
            if (otp == null) {
                throw new AssertionError("No OTP cached for user: " + user.getEmail());
            }
            String regBody = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/device/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "tempToken", tempToken,
                                    "otp", otp,
                                    "deviceName", "Test Computer",
                                    "devicePlatform", "Windows",
                                    "devicePublicKey", "test-public-key"
                            ))))
                    .andReturn().getResponse().getContentAsString();
            JsonNode regNode = objectMapper.readTree(regBody);
            if (!regNode.path("success").asBoolean()) {
                throw new AssertionError("Device registration failed: " + regBody);
            }
            JsonNode regData = regNode.path("data");
            String token = regData.path("accessToken").asText();
            String deviceId = regData.path("deviceId").asText();
            String deviceToken = regData.path("deviceToken").asText();
            DeviceCredentials newCreds = new DeviceCredentials(deviceId, deviceToken);
            tokenDeviceMap.put(token, newCreds);
            userDeviceMap.put(identifier, newCreds);
            return token;
        } else if ("DEVICE_LINK_REQUIRED".equals(deviceStatus)) {
            String tempToken = data.path("tempToken").asText();
            User user = findUser(identifier);
            if (user == null) {
                throw new AssertionError("User not found for test login: " + identifier);
            }
            String otp = deviceBindingService.getLatestOtpForTesting(user.getId());
            if (otp == null) {
                throw new AssertionError("No OTP cached for user: " + user.getEmail());
            }
            String linkBody = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/device/link-browser")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "tempToken", tempToken,
                                    "otp", otp,
                                    "devicePlatform", "Windows",
                                    "devicePublicKey", "test-public-key"
                            ))))
                    .andReturn().getResponse().getContentAsString();
            JsonNode linkNode = objectMapper.readTree(linkBody);
            if (!linkNode.path("success").asBoolean()) {
                throw new AssertionError("Device browser link failed: " + linkBody);
            }
            JsonNode linkData = linkNode.path("data");
            String token = linkData.path("accessToken").asText();
            String deviceId = linkData.path("deviceId").asText();
            String deviceToken = linkData.path("deviceToken").asText();
            DeviceCredentials newCreds = new DeviceCredentials(deviceId, deviceToken);
            tokenDeviceMap.put(token, newCreds);
            userDeviceMap.put(identifier, newCreds);
            return token;
        }

        String token = data.path("accessToken").asText();
        String deviceId = data.path("deviceId").asText(null);
        String deviceToken = data.path("deviceToken").asText(null);
        if (deviceId != null && deviceToken != null && !deviceId.isBlank() && !deviceToken.isBlank()) {
            DeviceCredentials c = new DeviceCredentials(deviceId, deviceToken);
            tokenDeviceMap.put(token, c);
            userDeviceMap.put(identifier, c);
        }
        return token;
    }

    public ResultActions get(String token, String path) throws Exception {
        return mockMvc.perform(auth(MockMvcRequestBuilders.get(path), token));
    }

    public ResultActions post(String token, String path, Object body) throws Exception {
        return mockMvc.perform(auth(MockMvcRequestBuilders.post(path), token).contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    /** POST without a body (action endpoints such as .../start or .../approve). */
    public ResultActions post(String token, String path) throws Exception {
        return mockMvc.perform(auth(MockMvcRequestBuilders.post(path), token));
    }

    public ResultActions put(String token, String path, Object body) throws Exception {
        return mockMvc.perform(auth(MockMvcRequestBuilders.put(path), token).contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    public ResultActions patch(String token, String path, Object body) throws Exception {
        MockHttpServletRequestBuilder b = auth(MockMvcRequestBuilders.patch(path), token);
        if (body != null) {
            b = b.contentType(MediaType.APPLICATION_JSON).content(json(body));
        }
        return mockMvc.perform(b);
    }

    public ResultActions delete(String token, String path) throws Exception {
        return mockMvc.perform(auth(MockMvcRequestBuilders.delete(path), token));
    }

    public JsonNode data(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString()).path("data");
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
        if (token == null) return b;
        b = b.header("Authorization", "Bearer " + token);
        DeviceCredentials creds = tokenDeviceMap.get(token);
        if (creds != null) {
            b = b.header("X-Device-Id", creds.deviceId())
                 .header("X-Device-Token", creds.deviceToken());
        }
        return b;
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }
}
