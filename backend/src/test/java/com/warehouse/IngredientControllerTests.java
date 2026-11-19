package com.warehouse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IngredientControllerTests extends WarehouseIntegrationTestSupport {

    @DynamicPropertySource
    static void configureSchema(DynamicPropertyRegistry registry) {
        isolateTestSchema(registry, "ingredients");
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
    void reusesIngredientWithoutMovementAndKeepsSameNameInOtherTypesDistinct() throws Exception {
        createIngredient("Farinha de Trigo", "DRY");

        create("""
                {"nome":"  FARINHA   DE trigo  ","tipo":"DRY"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nome").value("Farinha de Trigo"))
                .andExpect(jsonPath("$.quantidade").isEmpty())
                .andExpect(jsonPath("$.movimentacao").isEmpty());

        createIngredient("Farinha de Trigo", "REFRIGERATED");
        assertThat(count("ingredient")).isEqualTo(2);
        assertThat(count("compartment_stock")).isZero();
        assertThat(count("stock_movement")).isZero();
    }

    @Test
    void topsUpExistingIngredientWithAndWithoutAnExistingBalance() throws Exception {
        createIngredient("Arroz", "DRY");

        create("""
                {"nome":" arroz ","tipo":"DRY","quantidade":100}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.compartimento.codigo").value("C1"))
                .andExpect(jsonPath("$.movimentacao.responsavel").value(OPERATOR_LOGIN));

        createWithStock("ARROZ", "DRY", 20, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.quantidade").value(20))
                .andExpect(jsonPath("$.movimentacao.tipo").value("INBOUND"));

        create("""
                {"nome":"Arroz","tipo":"DRY"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.movimentacao").isEmpty());

        assertThat(count("ingredient")).isEqualTo(1);
        assertThat(count("compartment_stock")).isEqualTo(1);
        assertThat(count("stock_movement")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT quantity FROM compartment_stock", BigDecimal.class))
                .isEqualByComparingTo("120");
    }

    @Test
    void automaticPlacementSkipsIncompatibleTemporarilyRestrictedAndFullCompartments() throws Exception {
        createWithStock("Leite", "LIQUID", 100, 1).andExpect(status().isCreated());
        createWithStock("Suco", "LIQUID", 100, 2).andExpect(status().isCreated());
        movement("saida", 2, 2, "100").andExpect(status().isCreated());
        createWithStock("Farinha", "DRY", 600, 3).andExpect(status().isCreated());
        createIngredient("Arroz", "DRY");

        create("""
                {"nome":"Arroz","tipo":"DRY","quantidade":100}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compartimento.codigo").value("C4"));
    }

    @Test
    void rejectsInvalidOrExcessiveTopUpsWithoutChangingExistingStock() throws Exception {
        createWithStock("Arroz", "DRY", 500, 1).andExpect(status().isCreated());

        create("""
                {"nome":"Arroz","tipo":"DRY","quantidade":0}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        createWithStock("Arroz", "DRY", 101, 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_CAPACITY"));
        create("""
                {"nome":"Arroz","tipo":"DRY","quantidade":601}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_CAPACITY"));

        assertThat(count("ingredient")).isEqualTo(1);
        assertThat(count("stock_movement")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT quantity FROM compartment_stock", BigDecimal.class))
                .isEqualByComparingTo("500");
    }

    @Test
    void reusesLowestLegacyIdAndPreservesStockAndHistoryOfOtherRecords() throws Exception {
        createWithStock("Arroz", "DRY", 20, 1).andExpect(status().isCreated());
        jdbc.update("""
                INSERT INTO ingredient (name, ingredient_type_id)
                SELECT '  ARROZ  ', ingredient_type_id FROM ingredient WHERE id = 1
                """);
        movement("entrada", 2, 1, "30").andExpect(status().isCreated());

        createWithStock("arroz", "DRY", 10, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        assertThat(count("ingredient")).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT quantity FROM compartment_stock WHERE ingredient_id = 2", BigDecimal.class))
                .isEqualByComparingTo("30");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM stock_movement WHERE ingredient_id = 2", Integer.class))
                .isEqualTo(1);
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
                SELECT c.status = 'OCCUPIED' AND t.name = 'DRY' AND c.last_movement_at IS NULL
                FROM compartment c
                JOIN ingredient_type t ON t.id = c.current_type_id
                WHERE c.id = 1
                """, Boolean.class)).isTrue();
    }

    @Test
    void explainsRequiredCompartmentWhenCreatingWithInitialStock() throws Exception {
        create("""
                {"nome":"Arroz","tipo":"DRY","quantidade":1}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(
                        "Para cadastrar um ingrediente novo com estoque inicial, envie compartimentoId junto com quantidade. "
                                + "Para cadastrar sem estoque, omita quantidade."));
        assertThat(count("ingredient")).isZero();
    }

    @Test
    void explainsRequiredQuantityWhenCompartmentIsProvided() throws Exception {
        create("""
                {"nome":"Arroz","tipo":"DRY","compartimentoId":1}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(
                        "Envie quantidade junto com compartimentoId para registrar uma entrada, "
                                + "ou omita compartimentoId para cadastrar sem estoque."));
        assertThat(count("ingredient")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"nome\":\"Arroz\",\"tipo\":\"DESCONHECIDO\"}",
            "{\"nome\":\"Arroz\",\"tipo\":\"DRY\",\"quantidade\":0,\"compartimentoId\":1}",
            "{\"nome\":\"Arroz\",\"tipo\":\"DRY\",\"quantidade\":-1,\"compartimentoId\":1}",
            "{\"nome\":\"Arroz\",\"tipo\":\"DRY\",\"quantidade\":\"abc\",\"compartimentoId\":1}"
    })
    void rejectsInvalidRequests(String body) throws Exception {
        create(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(count("ingredient")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"100", "100.1", "100.12", "100.125"})
    void preservesDecimalQuantityInResponseStockHistoryAndVolume(String quantity) throws Exception {
        BigDecimal expected = new BigDecimal(quantity);
        create("""
                {"nome":"Arroz","tipo":"DRY","quantidade":%s,"compartimentoId":1}
                """.formatted(quantity))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantidade", comparesEqualTo(expected), BigDecimal.class))
                .andExpect(jsonPath("$.movimentacao.quantidade", comparesEqualTo(expected), BigDecimal.class));

        assertThat(jdbc.queryForObject("SELECT quantity FROM compartment_stock", BigDecimal.class))
                .isEqualByComparingTo(expected);
        assertThat(jdbc.queryForObject("SELECT quantity FROM stock_movement", BigDecimal.class))
                .isEqualByComparingTo(expected);
        mvc.perform(get("/ingredientes/volume").header(AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].quantidadeTotal", comparesEqualTo(expected), BigDecimal.class));
    }

    @Test
    void rejectsFourthDecimalPlaceWithoutRoundingOrWriting() throws Exception {
        create("""
                {"nome":"Arroz","tipo":"DRY","quantidade":100.1251,"compartimentoId":1}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(count("ingredient")).isZero();
        assertThat(count("compartment_stock")).isZero();
        assertThat(count("stock_movement")).isZero();
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
}
