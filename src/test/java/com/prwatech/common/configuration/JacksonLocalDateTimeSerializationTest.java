package com.prwatech.common.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.prwatech.skillama.dto.UserCourseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonLocalDateTimeSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        JavaTimeModule module = new JavaTimeModule();
        module.addSerializer(
                LocalDateTime.class,
                new LocalDateTimeSerializer(JacksonConfig.ISO_LOCAL_DATE_TIME));
        module.addDeserializer(
                LocalDateTime.class,
                new LocalDateTimeDeserializer(JacksonConfig.ISO_LOCAL_DATE_TIME));

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(module);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    void userCourseDto_serializesLastAccessedAsIsoString() throws Exception {
        UserCourseDTO dto = new UserCourseDTO();
        dto.setLastAccessed(LocalDateTime.of(2026, 5, 20, 16, 44, 11));
        dto.setEnrolledAt(LocalDateTime.of(2026, 1, 15, 9, 0, 0));

        String json = objectMapper.writeValueAsString(dto);

        assertTrue(json.contains("\"lastAccessed\":\"2026-05-20T16:44:11\""));
        assertTrue(json.contains("\"enrolledAt\":\"2026-01-15T09:00:00\""));
        assertFalse(json.contains("[2026"), "LocalDateTime must not serialize as array");
    }

    @Test
    void roundTrip_isoString() throws Exception {
        String json = "{\"lastAccessed\":\"2026-05-20T16:44:11\"}";
        UserCourseDTO dto = objectMapper.readValue(json, UserCourseDTO.class);
        assertTrue(dto.getLastAccessed().equals(LocalDateTime.of(2026, 5, 20, 16, 44, 11)));
    }
}
