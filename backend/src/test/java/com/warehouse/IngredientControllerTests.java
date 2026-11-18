package com.warehouse;

import com.warehouse.repository.AppUserRepository;
import com.warehouse.security.CustomUserDetails;
import com.warehouse.security.JwtService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class IngredientControllerTests {

    private static final String SCHEMA = "ingredients_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String OPERATOR_LOGIN = "operador-teste";

    @DynamicPropertySource
    static void isolateTestSchema(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.data-source-properties.currentSchema", () -> SCHEMA);
    }

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AppUserRepository users;
    @Autowired private JwtService jwtService;

    private String authorization;
    private long userId;

    @BeforeEach
    void prepareFixtures() {
        assertThat(jdbc.queryForObject("SELECT current_schema()", String.class)).isEqualTo(SCHEMA);
        jdbc.execute("TRUNCATE stock_movement, compartment_stock, ingredient RESTART IDENTITY");
        jdbc.update("UPDATE compartment SET status = 'EMPTY', current_type_id = NULL, last_movement_at = NULL");
        jdbc.update("""
                INSERT INTO app_user (name, login, password_hash)
                SELECT 'Operador de teste', ?, password_hash FROM app_user WHERE login = 'admin'
                ON CONFLICT (login) DO NOTHING
                """, OPERATOR_LOGIN);
        var user = users.findByLogin(OPERATOR_LOGIN).orElseThrow();
        userId = user.getId();
        authorization = "Bearer " + jwtService.generateToken(new CustomUserDetails(user));
    }

    @AfterAll
    static void dropTestSchema(@Autowired JdbcTemplate jdbc) {
        jdbc.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }

    @Test
    void createsIngredientWithoutStockOrMovement() throws Exception {
        create("""
                {"nome":"Arroz","tipo":"DRY"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nome").value("Arroz"))
                .andExpect(jsonPath("$.tipo").value("DRY"))
                .andExpect(jsonPath("$.quantidade").isEmpty())
                .andExpect(jsonPath("$.compartimento").isEmpty())
                .andExpect(jsonPath("$.movimentacao").isEmpty());

        assertThat(count("ingredient")).isEqualTo(1);
        assertThat(count("compartment_stock")).isZero();
        assertThat(count("stock_movement")).isZero();
    }

    @Test
    void createsInitialStockAndRecordsAuthenticatedUser() throws Exception {
        create("""
                {"nome":"Feijão","tipo":"DRY","quantidade":100.125,"compartimentoId":1}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nome").value("Feijão"))
                .andExpect(jsonPath("$.tipo").value("DRY"))
                .andExpect(jsonPath("$.quantidade").value(100.125))
                .andExpect(jsonPath("$.compartimento.id").value(1))
                .andExpect(jsonPath("$.compartimento.codigo").value("C1"))
                .andExpect(jsonPath("$.movimentacao.id").isNumber())
                .andExpect(jsonPath("$.movimentacao.tipo").value("INBOUND"))
                .andExpect(jsonPath("$.movimentacao.quantidade").value(100.125))
                .andExpect(jsonPath("$.movimentacao.responsavel").value(OPERATOR_LOGIN))
                .andExpect(jsonPath("$.movimentacao.dataHora").isString());

        assertThat(jdbc.queryForObject("SELECT quantity FROM compartment_stock", BigDecimal.class))
                .isEqualByComparingTo("100.125");
        assertThat(jdbc.queryForObject("SELECT user_id FROM stock_movement", Long.class)).isEqualTo(userId);
        assertThat(jdbc.queryForObject("""
                SELECT c.status = 'OCCUPIED' AND t.name = 'DRY' AND c.last_movement_at = m.created_at
                FROM compartment c
                JOIN ingredient_type t ON t.id = c.current_type_id
                JOIN stock_movement m ON m.compartment_id = c.id
                WHERE c.id = 1
                """, Boolean.class)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"nome\":\"Arroz\",\"tipo\":\"DRY\",\"quantidade\":1}",
            "{\"nome\":\"Arroz\",\"tipo\":\"DRY\",\"compartimentoId\":1}",
            "{\"nome\":\"Arroz\",\"tipo\":\"DESCONHECIDO\"}",
            "{\"nome\":\"Arroz\",\"tipo\":\"DRY\",\"quantidade\":0,\"compartimentoId\":1}",
            "{\"nome\":\"Arroz\",\"tipo\":\"DRY\",\"quantidade\":-1,\"compartimentoId\":1}",
            "{\"nome\":\"Arroz\",\"tipo\":\"DRY\",\"quantidade\":1.0005,\"compartimentoId\":1}",
            "{\"nome\":\"Arroz\",\"tipo\":\"DRY\",\"quantidade\":\"abc\",\"compartimentoId\":1}"
    })
    void rejectsInvalidRequests(String body) throws Exception {
        create(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(count("ingredient")).isZero();
    }

    @Test
    void rejectsUnknownCompartment() throws Exception {
        createWithStock("Arroz", "DRY", 1, 999)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMPARTMENT_NOT_FOUND"));
        assertThat(count("ingredient")).isZero();
    }

    @Test
    void rejectsQuantityAboveRemainingCapacity() throws Exception {
        createWithStock("Arroz", "DRY", 500, 1).andExpect(status().isCreated());
        createWithStock("Feijão", "DRY", 101, 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_CAPACITY"));

        assertThat(count("ingredient")).isEqualTo(1);
        assertThat(count("stock_movement")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT quantity FROM compartment_stock", BigDecimal.class))
                .isEqualByComparingTo("500");
    }

    @Test
    void rejectsDifferentTypeInOccupiedCompartment() throws Exception {
        createWithStock("Arroz", "DRY", 10, 1).andExpect(status().isCreated());
        createWithStock("Leite", "LIQUID", 10, 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INCOMPATIBLE_INGREDIENT_TYPE"));
        assertThat(count("ingredient")).isEqualTo(1);
    }

    @Test
    void allowsChangingTypeOfEmptyCompartmentOnlyFromNextDay() throws Exception {
        var now = LocalDateTime.now();
        jdbc.update("""
                UPDATE compartment SET current_type_id = (SELECT id FROM ingredient_type WHERE name = 'DRY'),
                    last_movement_at = ? WHERE id = 1
                """, now);

        createWithStock("Leite", "LIQUID", 10, 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMPARTMENT_TEMPORARILY_BLOCKED"));
        assertThat(count("ingredient")).isZero();

        jdbc.update("UPDATE compartment SET last_movement_at = ? WHERE id = 1", now.minusDays(1));
        createWithStock("Leite", "LIQUID", 10, 1).andExpect(status().isCreated());
    }

    @Test
    void rollsBackIngredientAndStockIfDatabaseRejectsMovement() throws Exception {
        jdbc.execute("ALTER TABLE stock_movement ADD CONSTRAINT reject_test_movement CHECK (quantity <> 17)");
        try {
            createWithStock("Feijão", "DRY", 17, 1)
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

            assertThat(count("ingredient")).isZero();
            assertThat(count("stock_movement")).isZero();
            assertThat(count("compartment_stock")).isZero();
            assertThat(jdbc.queryForObject("""
                    SELECT status = 'EMPTY' AND current_type_id IS NULL AND last_movement_at IS NULL
                    FROM compartment WHERE id = 1
                    """, Boolean.class)).isTrue();
        } finally {
            jdbc.execute("ALTER TABLE stock_movement DROP CONSTRAINT reject_test_movement");
        }
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mvc.perform(post("/ingredientes").contentType(APPLICATION_JSON).content("""
                {"nome":"Arroz","tipo":"DRY"}
                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void listsIngredientsAndTheirTypes() throws Exception {
        create("{\"nome\":\"Arroz\",\"tipo\":\"DRY\"}").andExpect(status().isCreated());
        create("{\"nome\":\"Leite\",\"tipo\":\"LIQUID\"}").andExpect(status().isCreated());

        mvc.perform(get("/ingredientes").header(AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nome").value("Arroz"))
                .andExpect(jsonPath("$[0].tipo").value("DRY"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].nome").value("Leite"))
                .andExpect(jsonPath("$[1].tipo").value("LIQUID"));
    }

    @Test
    void listsEmptyIngredients() throws Exception {
        mvc.perform(get("/ingredientes").header(AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void findsIngredientById() throws Exception {
        create("{\"nome\":\"Arroz\",\"tipo\":\"DRY\"}").andExpect(status().isCreated());

        mvc.perform(get("/ingredientes/{id}", 1).header(AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nome").value("Arroz"))
                .andExpect(jsonPath("$.tipo").value("DRY"));
    }

    @Test
    void returnsNotFoundForUnknownIngredient() throws Exception {
        mvc.perform(get("/ingredientes/{id}", 999).header(AUTHORIZATION, authorization))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INGREDIENT_NOT_FOUND"));
    }

    @Test
    void rejectsNonNumericIngredientId() throws Exception {
        mvc.perform(get("/ingredientes/{id}", "abc").header(AUTHORIZATION, authorization))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void totalsCurrentStockByTypeAndReturnsUnitsAndZeroBalances() throws Exception {
        createWithStock("Arroz", "DRY", 100, 1).andExpect(status().isCreated());
        createWithStock("Feijão", "DRY", 50, 1).andExpect(status().isCreated());
        createWithStock("Farinha", "DRY", 25, 2).andExpect(status().isCreated());
        createWithStock("Leite", "LIQUID", 120, 3).andExpect(status().isCreated());
        create("{\"nome\":\"Queijo\",\"tipo\":\"REFRIGERATED\"}").andExpect(status().isCreated());

        // Diverge do histórico de entradas para tornar observável a origem do saldo.
        jdbc.update("UPDATE compartment_stock SET quantity = 70 WHERE ingredient_id = 1");
        jdbc.update("""
                INSERT INTO stock_movement (user_id, compartment_id, ingredient_id, movement_type, quantity)
                VALUES (?, 1, 1, 'OUTBOUND', 15)
                """, userId);

        mvc.perform(get("/ingredientes/volume").header(AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].tipo").value("DRY"))
                .andExpect(jsonPath("$[0].quantidadeTotal").value(145))
                .andExpect(jsonPath("$[0].unidadeMedida").value("KG"))
                .andExpect(jsonPath("$[1].tipo").value("LIQUID"))
                .andExpect(jsonPath("$[1].quantidadeTotal").value(120))
                .andExpect(jsonPath("$[1].unidadeMedida").value("L"))
                .andExpect(jsonPath("$[2].tipo").value("REFRIGERATED"))
                .andExpect(jsonPath("$[2].quantidadeTotal").value(0))
                .andExpect(jsonPath("$[2].unidadeMedida").value("KG"));
    }

    private ResultActions create(String body) throws Exception {
        return mvc.perform(post("/ingredientes").header(AUTHORIZATION, authorization)
                .contentType(APPLICATION_JSON).content(body));
    }

    private ResultActions createWithStock(String name, String type, int quantity, int compartmentId) throws Exception {
        return create("""
                {"nome":"%s","tipo":"%s","quantidade":%d,"compartimentoId":%d}
                """.formatted(name, type, quantity, compartmentId));
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
