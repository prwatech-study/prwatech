package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserMapperTest {

    @Test
    void toSessionDto_mapsLightweightFieldsOnly() {
        User user = User.builder()
                .id("u1")
                .name("Ada")
                .email("ada@skillama.co.in")
                .password("hashed-secret")
                .role(User.UserRole.USER)
                .active(true)
                .build();

        UserSessionDTO dto = UserMapper.toSessionDto(user);

        assertNotNull(dto);
        assertEquals("u1", dto.getId());
        assertEquals("Ada", dto.getName());
        assertEquals("ada@skillama.co.in", dto.getEmail());
    }

    @Test
    void toPublicDto_neverExposesPasswordField() {
        User user = User.builder()
                .id("u2")
                .name("Bob")
                .email("bob@skillama.co.in")
                .password("bcrypt-hash")
                .role(User.UserRole.ADMIN)
                .active(true)
                .build();

        UserPublicDTO dto = UserMapper.toPublicDto(user);

        assertNotNull(dto);
        assertEquals("u2", dto.getId());
        assertEquals(User.UserRole.ADMIN, dto.getRole());
        // DTO has no password field — mapping must not copy secrets
        assertNull(dto.getClass().getDeclaredFields().length == 0 ? null : findPasswordField(dto));
    }

    @Test
    void toLoginResponse_neverExposesPasswordField() {
        User user = User.builder()
                .id("u3")
                .name("Cara")
                .email("cara@skillama.co.in")
                .password("bcrypt-hash")
                .role(User.UserRole.USER)
                .active(true)
                .build();

        LoginResponseDTO dto = UserMapper.toLoginResponse(user, "jwt-token", null);

        assertNotNull(dto);
        assertEquals("jwt-token", dto.getToken());
        assertNull(findPasswordField(dto));
    }

    private static Object findPasswordField(Object dto) {
        try {
            dto.getClass().getDeclaredField("password");
            return "unexpected-password-field";
        } catch (NoSuchFieldException e) {
            return null;
        }
    }
}
