package com.lordsai.lsi.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Map;


/** Small helper so tests read like API calls: login once, then call with the token. */
@Component
@Lazy
public class ApiClient {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public ApiClient(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    public String login(String identifier, String password) throws Exception {
        String body = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("identifier", identifier, "password", password))))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(body);
        if (!node.path("success").asBoolean()) {
            throw new AssertionError("Login failed: " + body);
        }
        return node.path("data").path("accessToken").asText();
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
        return token == null ? b : b.header("Authorization", "Bearer " + token);
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }
}
