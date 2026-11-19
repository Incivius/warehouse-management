package com.warehouse;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "jwt.expiration=900000")
class AuthenticationTests extends WarehouseIntegrationTestSupport {

    @DynamicPropertySource
    static void configureSchema(DynamicPropertyRegistry registry) {
        isolateTestSchema(registry, "auth");
    }

    @Test
    void acceptsTheInitialAdministratorCredentials() throws Exception {
        assertThat(jdbc.queryForList("""
                SELECT login FROM app_user
                WHERE login IN ('admin', 'operador1', 'operador2')
                """, String.class)).containsExactlyInAnyOrder("admin", "operador1", "operador2");

        mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content("""
                                {"login":"admin","senha":"admin123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.login").value("admin"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mvc.perform(post("/ingredientes").contentType(APPLICATION_JSON).content("""
                {"nome":"Arroz","tipo":"DRY"}
                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
