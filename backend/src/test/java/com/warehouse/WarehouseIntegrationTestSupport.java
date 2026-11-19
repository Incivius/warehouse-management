package com.warehouse;

import com.warehouse.repository.AppUserRepository;
import com.warehouse.security.CustomUserDetails;
import com.warehouse.security.JwtService;
import com.warehouse.service.StockService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class WarehouseIntegrationTestSupport {

    protected static final String OPERATOR_LOGIN = "operador-teste";

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired private AppUserRepository users;
    @Autowired private JwtService jwtService;
    @Autowired private StockService stockService;
    @Value("${spring.flyway.default-schema}")
    protected String schema;

    protected String authorization;
    protected long userId;
    private CustomUserDetails operator;

    protected static void isolateTestSchema(DynamicPropertyRegistry registry, String context) {
        String schema = context + "_test_" + UUID.randomUUID().toString().replace("-", "");
        registry.add("spring.flyway.default-schema", () -> schema);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> schema);
        registry.add("spring.datasource.hikari.data-source-properties.currentSchema", () -> schema);
    }

    @BeforeEach
    void prepareFixtures() {
        assertThat(jdbc.queryForObject("SELECT current_schema()", String.class)).isEqualTo(schema);
        jdbc.execute("TRUNCATE stock_movement, compartment_stock, ingredient RESTART IDENTITY");
        jdbc.update("UPDATE compartment SET status = 'EMPTY', current_type_id = NULL, last_movement_at = NULL");
        jdbc.update("""
                INSERT INTO app_user (name, login, password_hash)
                SELECT 'Operador de teste', ?, password_hash FROM app_user WHERE login = 'admin'
                ON CONFLICT (login) DO NOTHING
                """, OPERATOR_LOGIN);
        var user = users.findByLogin(OPERATOR_LOGIN).orElseThrow();
        userId = user.getId();
        operator = new CustomUserDetails(user);
        authorization = "Bearer " + jwtService.generateToken(operator);
    }

    @AfterAll
    static void dropTestSchema(
            @Autowired JdbcTemplate jdbc,
            @Value("${spring.flyway.default-schema}") String schema
    ) {
        jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
    }

    protected ResultActions create(String body) throws Exception {
        return mvc.perform(post("/ingredientes").header(AUTHORIZATION, authorization)
                .contentType(APPLICATION_JSON).content(body));
    }

    protected void createIngredient(String name, String type) throws Exception {
        create("""
                {"nome":"%s","tipo":"%s"}
                """.formatted(name, type))
                .andExpect(status().isCreated());
    }

    protected ResultActions createWithStock(String name, String type, int quantity, int compartmentId) throws Exception {
        return create("""
                {"nome":"%s","tipo":"%s","quantidade":%d,"compartimentoId":%d}
                """.formatted(name, type, quantity, compartmentId));
    }

    protected ResultActions movement(String path, int ingredientId, int compartmentId, String quantity) throws Exception {
        return mvc.perform(post("/estoque/{operacao}", path).header(AUTHORIZATION, authorization)
                .contentType(APPLICATION_JSON).content("""
                        {"ingredienteId":%d,"compartimentoId":%d,"quantidade":%s}
                        """.formatted(ingredientId, compartmentId, quantity)));
    }

    protected void removeStock(long ingredientId, String quantity) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                operator, null, operator.getAuthorities()));
        SecurityContextHolder.setContext(context);
        try {
            stockService.removeStock(1L, ingredientId, new BigDecimal(quantity));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    protected LocalDateTime emptyTime() {
        return jdbc.queryForObject("SELECT last_movement_at FROM compartment WHERE id = 1", LocalDateTime.class);
    }

    protected int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
