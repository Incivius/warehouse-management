package com.warehouse;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StockHistoryTests extends WarehouseIntegrationTestSupport {

    @DynamicPropertySource
    static void configureSchema(DynamicPropertyRegistry registry) {
        isolateTestSchema(registry, "history");
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
}
