package com.warehouse;

import com.warehouse.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static com.warehouse.exception.ErrorCode.INSUFFICIENT_STOCK;
import static com.warehouse.exception.ErrorCode.INVALID_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "jwt.expiration=900000")
class StockMovementTests extends WarehouseIntegrationTestSupport {

    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void configureSchema(DynamicPropertyRegistry registry) {
        isolateTestSchema(registry, "stock");
    }

    @Test
    void recordsInboundAndOutboundAgainstTheSameStock() throws Exception {
        String password = UUID.randomUUID().toString();
        jdbc.update("UPDATE app_user SET password_hash = ? WHERE login = ?",
                passwordEncoder.encode(password), OPERATOR_LOGIN);
        String loginBody = mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content("""
                                {"login":"%s","senha":"%s"}
                                """.formatted(OPERATOR_LOGIN, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.login").value(OPERATOR_LOGIN))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn().getResponse().getContentAsString();
        authorization = "Bearer " + objectMapper.readTree(loginBody).get("accessToken").asText();

        createIngredient("Arroz", "DRY");
        movement("entrada", 1, 1, "5.125")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operacao").value("INBOUND"))
                .andExpect(jsonPath("$.ingredienteId").value(1))
                .andExpect(jsonPath("$.ingrediente").value("Arroz"))
                .andExpect(jsonPath("$.tipo").value("DRY"))
                .andExpect(jsonPath("$.quantidade").value(5.125))
                .andExpect(jsonPath("$.compartimentoId").value(1))
                .andExpect(jsonPath("$.compartimento").value("C1"))
                .andExpect(jsonPath("$.responsavel").value(OPERATOR_LOGIN))
                .andExpect(jsonPath("$.dataHora").isString());
        movement("entrada", 1, 1, "1.875").andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT quantity FROM compartment_stock", BigDecimal.class))
                .isEqualByComparingTo("7");

        movement("saida", 1, 1, "0.875")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operacao").value("OUTBOUND"))
                .andExpect(jsonPath("$.quantidade").value(0.875))
                .andExpect(jsonPath("$.responsavel").value(OPERATOR_LOGIN));
        assertThat(jdbc.queryForObject("SELECT quantity FROM compartment_stock", BigDecimal.class))
                .isEqualByComparingTo("6.125");

        movement("saida", 1, 1, "6.125").andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM compartment_stock", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT c.status = 'EMPTY' AND t.name = 'DRY' AND c.last_movement_at = m.created_at
                FROM compartment c
                JOIN ingredient_type t ON t.id = c.current_type_id
                JOIN stock_movement m ON m.compartment_id = c.id
                WHERE c.id = 1 AND m.movement_type = 'OUTBOUND' AND m.quantity = 6.125
                """, Boolean.class)).isTrue();
        assertThat(jdbc.queryForList("""
                SELECT m.movement_type FROM stock_movement m JOIN app_user u ON u.id = m.user_id
                WHERE u.login = ? ORDER BY m.id
                """, String.class, OPERATOR_LOGIN)).containsExactly("INBOUND", "INBOUND", "OUTBOUND", "OUTBOUND");
    }

    @Test
    void rejectsInsufficientCapacityIncompatibleTypeAndInsufficientStock() throws Exception {
        createIngredient("Arroz", "DRY");
        createIngredient("Leite", "LIQUID");
        movement("entrada", 1, 1, "600").andExpect(status().isCreated());
        movement("entrada", 1, 1, "0.001").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_CAPACITY"));
        movement("entrada", 2, 1, "1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INCOMPATIBLE_INGREDIENT_TYPE"));
        movement("saida", 1, 1, "600.001").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        movement("saida", 2, 1, "1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        assertThat(jdbc.queryForObject("SELECT quantity FROM compartment_stock", BigDecimal.class))
                .isEqualByComparingTo("600");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_movement", Integer.class)).isEqualTo(1);
    }

    @Test
    void requiresAuthenticationAndValidMovementParameters() throws Exception {
        createIngredient("Arroz", "DRY");
        mvc.perform(post("/estoque/entrada").contentType(APPLICATION_JSON)
                        .content("{\"ingredienteId\":1,\"compartimentoId\":1,\"quantidade\":1}"))
                .andExpect(status().isUnauthorized());
        movement("saida", 1, 1, "1.0001").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        movement("entrada", 99, 1, "1").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INGREDIENT_NOT_FOUND"));
        movement("saida", 99, 1, "1").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INGREDIENT_NOT_FOUND"));
        movement("entrada", 1, 99, "1").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMPARTMENT_NOT_FOUND"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_movement", Integer.class)).isZero();
    }

    @Test
    void recordsEmptyTimeOnlyAfterTheLastStockIsRemoved() throws Exception {
        createWithStock("Arroz", "DRY", 10, 1).andExpect(status().isCreated());
        createWithStock("Feijão", "DRY", 20, 1).andExpect(status().isCreated());

        removeStock(1, "5.125");
        assertThat(jdbc.queryForObject("SELECT quantity FROM compartment_stock WHERE ingredient_id = 1", BigDecimal.class))
                .isEqualByComparingTo("4.875");
        assertThat(emptyTime()).isNull();

        removeStock(1, "4.875");
        assertThat(count("compartment_stock")).isEqualTo(1);
        assertThat(emptyTime()).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM compartment WHERE id = 1", String.class))
                .isEqualTo("OCCUPIED");

        removeStock(2, "20");
        assertThat(count("compartment_stock")).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT c.status = 'EMPTY' AND t.name = 'DRY' AND c.last_movement_at = m.created_at
                FROM compartment c
                JOIN ingredient_type t ON t.id = c.current_type_id
                JOIN stock_movement m ON m.compartment_id = c.id
                WHERE c.id = 1 AND m.ingredient_id = 2 AND m.movement_type = 'OUTBOUND'
                """, Boolean.class)).isTrue();
    }

    @Test
    void rejectsInvalidWithdrawalsWithoutChangingStockOrHistory() throws Exception {
        createWithStock("Arroz", "DRY", 10, 1).andExpect(status().isCreated());

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> removeStock(1, "10.001"))
                .extracting(BusinessException::getErrorCode).isEqualTo(INSUFFICIENT_STOCK);
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> removeStock(1, "9.9999"))
                .extracting(BusinessException::getErrorCode).isEqualTo(INVALID_REQUEST);

        assertThat(jdbc.queryForObject("SELECT quantity FROM compartment_stock", BigDecimal.class))
                .isEqualByComparingTo("10");
        assertThat(count("stock_movement")).isEqualTo(1);
        assertThat(emptyTime()).isNull();
    }
}
