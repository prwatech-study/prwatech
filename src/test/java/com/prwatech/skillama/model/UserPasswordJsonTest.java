package com.prwatech.skillama.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class UserPasswordJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserializesPasswordFromRequestBody() throws Exception {
        User user = mapper.readValue(
                "{\"email\":\"user@skillama.co.in\",\"password\":\"plain-secret\"}",
                User.class);

        assertEquals("user@skillama.co.in", user.getEmail());
        assertEquals("plain-secret", user.getPassword());
    }

    @Test
    void doesNotSerializePasswordInResponses() throws Exception {
        User user = User.builder()
                .email("user@skillama.co.in")
                .password("hashed-secret")
                .build();

        String json = mapper.writeValueAsString(user);

        assertFalse(json.contains("hashed-secret"));
        assertFalse(json.contains("password"));
    }

    @Test
    void registerRequest_deserializesPasswordForEncoding() throws Exception {
        User user = mapper.readValue(
                "{\"name\":\"Ada\",\"email\":\"ada@skillama.co.in\",\"password\":\"signup-pass\",\"phone\":\"9876543210\"}",
                User.class);

        assertEquals("Ada", user.getName());
        assertEquals("signup-pass", user.getPassword());
    }
}
