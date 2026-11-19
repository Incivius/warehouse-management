package com.warehouse;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StockSnapshotTests extends WarehouseIntegrationTestSupport {

    @DynamicPropertySource
    static void configureSchema(DynamicPropertyRegistry registry) {
        isolateTestSchema(registry, "stock_snapshot");
    }

    @Test
    void returnsAllEmptyCompartmentsAndRequiresJwt() throws Exception {
        mvc.perform(get("/estoque"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mvc.perform(get("/estoque").header(AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compartimentos.length()").value(5))
                .andExpect(jsonPath("$.compartimentos[0].id").value(1))
                .andExpect(jsonPath("$.compartimentos[0].codigo").value("C1"))
                .andExpect(jsonPath("$.compartimentos[0].status").value("EMPTY"))
                .andExpect(jsonPath("$.compartimentos[0].tipo").isEmpty())
                .andExpect(jsonPath("$.compartimentos[0].capacidadeMaxima").value(600))
                .andExpect(jsonPath("$.compartimentos[0].unidadeMedida").value("KG"))
                .andExpect(jsonPath("$.compartimentos[0].quantidadeOcupada").value(0))
                .andExpect(jsonPath("$.compartimentos[0].capacidadeDisponivel").value(600))
                .andExpect(jsonPath("$.compartimentos[0].percentualOcupacao").value(0))
                .andExpect(jsonPath("$.compartimentos[0].ultimaMovimentacao").isEmpty())
                .andExpect(jsonPath("$.compartimentos[0].ingredientes").isEmpty())
                .andExpect(jsonPath("$.compartimentos[4].codigo").value("C5"));
    }

    @Test
    void groupsCurrentBalancesAndReportsLatestMovementWithoutWritingData() throws Exception {
        createWithStock("Arroz", "DRY", 200, 1).andExpect(status().isCreated());
        createWithStock("Farinha", "DRY", 151, 1).andExpect(status().isCreated());
        createWithStock("Leite", "LIQUID", 125, 2).andExpect(status().isCreated());
        createWithStock("Suco", "LIQUID", 30, 3).andExpect(status().isCreated());
        movement("saida", 4, 3, "30").andExpect(status().isCreated());

        // O saldo atual diverge do histórico para verificar a origem dos dados.
        jdbc.update("UPDATE compartment_stock SET quantity = 150 WHERE ingredient_id = 2");
        var latest = LocalDateTime.of(2026, 11, 18, 14, 30);
        jdbc.update("UPDATE stock_movement SET created_at = ?", latest.minusHours(1));
        jdbc.update("UPDATE stock_movement SET created_at = ? WHERE id IN (2, 5)", latest);
        String before = databaseState();

        mvc.perform(get("/estoque").header(AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compartimentos.length()").value(5))
                .andExpect(jsonPath("$.compartimentos[0].status").value("OCCUPIED"))
                .andExpect(jsonPath("$.compartimentos[0].tipo").value("DRY"))
                .andExpect(jsonPath("$.compartimentos[0].capacidadeMaxima").value(600))
                .andExpect(jsonPath("$.compartimentos[0].quantidadeOcupada").value(350))
                .andExpect(jsonPath("$.compartimentos[0].capacidadeDisponivel").value(250))
                .andExpect(jsonPath("$.compartimentos[0].percentualOcupacao").value(58.33))
                .andExpect(jsonPath("$.compartimentos[0].ultimaMovimentacao").value("2026-11-18T14:30:00"))
                .andExpect(jsonPath("$.compartimentos[0].ingredientes.length()").value(2))
                .andExpect(jsonPath("$.compartimentos[0].ingredientes[0].estoqueId").value(1))
                .andExpect(jsonPath("$.compartimentos[0].ingredientes[0].ingredienteId").value(1))
                .andExpect(jsonPath("$.compartimentos[0].ingredientes[0].nome").value("Arroz"))
                .andExpect(jsonPath("$.compartimentos[0].ingredientes[0].tipo").value("DRY"))
                .andExpect(jsonPath("$.compartimentos[0].ingredientes[0].quantidade").value(200))
                .andExpect(jsonPath("$.compartimentos[0].ingredientes[0].unidadeMedida").value("KG"))
                .andExpect(jsonPath("$.compartimentos[0].ingredientes[1].quantidade").value(150))
                .andExpect(jsonPath("$.compartimentos[1].id").value(2))
                .andExpect(jsonPath("$.compartimentos[1].tipo").value("LIQUID"))
                .andExpect(jsonPath("$.compartimentos[1].unidadeMedida").value("L"))
                .andExpect(jsonPath("$.compartimentos[1].capacidadeDisponivel").value(375))
                .andExpect(jsonPath("$.compartimentos[1].percentualOcupacao").value(25))
                .andExpect(jsonPath("$.compartimentos[1].ingredientes.length()").value(1))
                .andExpect(jsonPath("$.compartimentos[2].status").value("EMPTY"))
                .andExpect(jsonPath("$.compartimentos[2].tipo").isEmpty())
                .andExpect(jsonPath("$.compartimentos[2].ingredientes").isEmpty())
                .andExpect(jsonPath("$.compartimentos[2].ultimaMovimentacao").value("2026-11-18T14:30:00"));

        assertThat(databaseState()).isEqualTo(before);
    }

    private String databaseState() {
        return jdbc.queryForObject("""
                SELECT jsonb_build_array(
                    (SELECT jsonb_agg(i ORDER BY i.id) FROM ingredient i),
                    (SELECT jsonb_agg(c ORDER BY c.id) FROM compartment c),
                    (SELECT jsonb_agg(s ORDER BY s.id) FROM compartment_stock s),
                    (SELECT jsonb_agg(m ORDER BY m.id) FROM stock_movement m)
                )::text
                """, String.class);
    }
}
