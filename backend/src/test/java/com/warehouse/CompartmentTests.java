package com.warehouse;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompartmentTests extends WarehouseIntegrationTestSupport {

    @DynamicPropertySource
    static void configureSchema(DynamicPropertyRegistry registry) {
        isolateTestSchema(registry, "compartments");
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
    void rejectsDifferentTypeOnTheDayTheCompartmentWasEmptied() throws Exception {
        emptyDryCompartment();
        createWithStock("Leite", "LIQUID", 10, 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMPARTMENT_TEMPORARILY_BLOCKED"));
        assertThat(count("ingredient")).isEqualTo(1);
        assertThat(count("compartment_stock")).isZero();
    }

    @Test
    void allowsDifferentTypeOnTheNextCalendarDay() throws Exception {
        emptyDryCompartment();
        var beforeMidnight = LocalDate.now().atStartOfDay().minusNanos(1_000);
        jdbc.update("UPDATE compartment SET last_movement_at = ? WHERE id = 1", beforeMidnight);

        createWithStock("Leite", "LIQUID", 10, 1).andExpect(status().isCreated());
        assertThat(emptyTime()).isEqualTo(beforeMidnight);
    }

    @Test
    void allowsRefillWithTheSameTypeOnTheDayItWasEmptied() throws Exception {
        emptyDryCompartment();
        var emptiedAt = emptyTime();
        createWithStock("Feijão", "DRY", 10, 1).andExpect(status().isCreated());
        assertThat(emptyTime()).isEqualTo(emptiedAt);
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

    private void emptyDryCompartment() throws Exception {
        createWithStock("Arroz", "DRY", 10, 1).andExpect(status().isCreated());
        removeStock(1, "10");
    }
}
