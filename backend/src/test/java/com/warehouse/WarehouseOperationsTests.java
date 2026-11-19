package com.warehouse;

import com.warehouse.repository.AppUserRepository;
import com.warehouse.security.CustomUserDetails;
import com.warehouse.security.JwtService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "jwt.expiration=900000")
@AutoConfigureMockMvc
class WarehouseOperationsTests {

    private static final String SCHEMA = "operations_test_" + UUID.randomUUID().toString().replace("-", "");
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
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private String authorization;

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
        authorization = "Bearer " + jwtService.generateToken(new CustomUserDetails(user));
    }

    @AfterAll
    static void dropTestSchema(@Autowired JdbcTemplate jdbc) {
        jdbc.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
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
    void listsOnlyCompartmentsWithCompatibleTypeAndEnoughFreeCapacity() throws Exception {
        createIngredient("Arroz", "DRY");
        createIngredient("Leite", "LIQUID");
        movement("entrada", 1, 1, "599.875").andExpect(status().isCreated());
        movement("entrada", 2, 2, "10").andExpect(status().isCreated());
        movement("entrada", 2, 3, "10").andExpect(status().isCreated());
        movement("saida", 2, 3, "10").andExpect(status().isCreated());
        movement("entrada", 2, 4, "10").andExpect(status().isCreated());
        movement("saida", 2, 4, "10").andExpect(status().isCreated());
        jdbc.update("UPDATE compartment SET last_movement_at = ? WHERE code = 'C4'",
                LocalDate.now().minusDays(1).atTime(23, 59));
        jdbc.update("UPDATE compartment SET status = 'BLOCKED' WHERE code = 'C5'");

        mvc.perform(get("/compartimentos/disponiveis").header(AUTHORIZATION, authorization)
                        .param("tipo", "DRY").param("quantidade", "0.125"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].codigo").value("C1"))
                .andExpect(jsonPath("$[0].quantidadeDisponivel").value(0.125))
                .andExpect(jsonPath("$[0].unidadeMedida").value("KG"))
                .andExpect(jsonPath("$[1].codigo").value("C4"))
                .andExpect(jsonPath("$[1].quantidadeDisponivel").value(600));

        mvc.perform(get("/compartimentos/disponiveis").header(AUTHORIZATION, authorization)
                        .param("tipo", "DRY").param("quantidade", "0.126"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].codigo").value("C4"));

        mvc.perform(get("/compartimentos/disponiveis").header(AUTHORIZATION, authorization)
                        .param("tipo", "LIQUID").param("quantidade", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].codigo").value("C3"))
                .andExpect(jsonPath("$[1].codigo").value("C4"))
                .andExpect(jsonPath("$[0].unidadeMedida").value("L"));
    }

    @Test
    void listsOnlyCompartmentsWithCurrentStockOfTheRequestedType() throws Exception {
        createIngredient("Arroz", "DRY");
        createIngredient("Feijão", "DRY");
        createIngredient("Leite", "LIQUID");
        movement("entrada", 1, 1, "10.125").andExpect(status().isCreated());
        movement("entrada", 2, 1, "20.250").andExpect(status().isCreated());
        movement("saida", 1, 1, "1").andExpect(status().isCreated());
        movement("entrada", 1, 2, "3").andExpect(status().isCreated());
        movement("saida", 1, 2, "3").andExpect(status().isCreated());
        movement("entrada", 3, 3, "15").andExpect(status().isCreated());

        mvc.perform(get("/compartimentos/disponiveis-para-venda").header(AUTHORIZATION, authorization)
                        .param("tipo", "DRY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].codigo").value("C1"))
                .andExpect(jsonPath("$[0].quantidadeDisponivel").value(29.375))
                .andExpect(jsonPath("$[0].unidadeMedida").value("KG"));

        mvc.perform(get("/compartimentos/disponiveis-para-venda").header(AUTHORIZATION, authorization)
                        .param("tipo", "REFRIGERATED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rejectsInvalidCompartmentFiltersAndRequiresAuthentication() throws Exception {
        mvc.perform(get("/compartimentos/disponiveis").header(AUTHORIZATION, authorization)
                        .param("tipo", "DRY"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/compartimentos/disponiveis").header(AUTHORIZATION, authorization)
                        .param("tipo", "DRY").param("quantidade", "1.0001"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/compartimentos/disponiveis-para-venda").header(AUTHORIZATION, authorization)
                        .param("tipo", "DESCONHECIDO"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/compartimentos/disponiveis-para-venda").param("tipo", "DRY"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsMovementHistoryWithStableSortingAndPagination() throws Exception {
        createIngredient("Arroz", "DRY");
        movement("entrada", 1, 2, "10").andExpect(status().isCreated());
        movement("entrada", 1, 1, "5").andExpect(status().isCreated());
        movement("entrada", 1, 1, "5").andExpect(status().isCreated());
        movement("saida", 1, 1, "1").andExpect(status().isCreated());
        var newer = LocalDate.now().atTime(11, 0);
        jdbc.update("UPDATE stock_movement SET created_at = ?", newer);
        jdbc.update("UPDATE stock_movement SET created_at = ? WHERE id = 2", newer.minusHours(1));

        mvc.perform(get("/historico").header(AUTHORIZATION, authorization).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(4))
                .andExpect(jsonPath("$.page.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(3))
                .andExpect(jsonPath("$.content[1].id").value(4))
                .andExpect(jsonPath("$.content[1].operacao").value("OUTBOUND"))
                .andExpect(jsonPath("$.content[1].ingredienteId").value(1))
                .andExpect(jsonPath("$.content[1].ingrediente").value("Arroz"))
                .andExpect(jsonPath("$.content[1].tipo").value("DRY"))
                .andExpect(jsonPath("$.content[1].quantidade").value(1))
                .andExpect(jsonPath("$.content[1].compartimento").value("C1"))
                .andExpect(jsonPath("$.content[1].responsavel").value(OPERATOR_LOGIN))
                .andExpect(jsonPath("$.content[1].dataHora").isString());

        mvc.perform(get("/historico").header(AUTHORIZATION, authorization).param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[1].id").value(2));

        mvc.perform(get("/historico").header(AUTHORIZATION, authorization)
                        .param("sortBy", "date").param("order", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(2))
                .andExpect(jsonPath("$.content[3].id").value(1));

        mvc.perform(get("/historico").header(AUTHORIZATION, authorization)
                        .param("sortBy", "compartment").param("order", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[1].id").value(3))
                .andExpect(jsonPath("$.content[2].id").value(4))
                .andExpect(jsonPath("$.content[3].id").value(2));
    }

    @Test
    void returnsEmptyPageAndRejectsInvalidHistoryParameters() throws Exception {
        mvc.perform(get("/historico").header(AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(0));
        mvc.perform(get("/historico").header(AUTHORIZATION, authorization).param("sortBy", "user"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/historico").header(AUTHORIZATION, authorization).param("order", "invalida"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/historico").header(AUTHORIZATION, authorization).param("size", "101"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/historico").header(AUTHORIZATION, authorization).param("page", "-1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/historico")).andExpect(status().isUnauthorized());
    }

    private void createIngredient(String name, String type) throws Exception {
        mvc.perform(post("/ingredientes").header(AUTHORIZATION, authorization).contentType(APPLICATION_JSON)
                        .content("""
                                {"nome":"%s","tipo":"%s"}
                                """.formatted(name, type)))
                .andExpect(status().isCreated());
    }

    private ResultActions movement(String path, int ingredientId, int compartmentId, String quantity) throws Exception {
        return mvc.perform(post("/estoque/{operacao}", path).header(AUTHORIZATION, authorization)
                .contentType(APPLICATION_JSON).content("""
                        {"ingredienteId":%d,"compartimentoId":%d,"quantidade":%s}
                        """.formatted(ingredientId, compartmentId, quantity)));
    }
}
