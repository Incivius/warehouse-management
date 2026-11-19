package com.warehouse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class StockConcurrencyTests extends WarehouseIntegrationTestSupport {

    @Autowired private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void configureSchema(DynamicPropertyRegistry registry) {
        isolateTestSchema(registry, "concurrency");
    }

    @Test
    void serializesConcurrentEntriesForTheSameCompartment() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var transaction = new TransactionTemplate(transactionManager);
            var requests = transaction.execute(ignored -> {
                // Segura a linha até as duas requisições chegarem ao bloqueio no PostgreSQL.
                jdbc.queryForObject("SELECT id FROM compartment WHERE id = 1 FOR NO KEY UPDATE", Long.class);
                var first = executor.submit(() -> createWithStock("Arroz", "DRY", 400, 1));
                var second = executor.submit(() -> createWithStock("Feijão", "DRY", 400, 1));

                await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                        assertThat(jdbc.queryForObject("""
                                SELECT count(*) FROM pg_stat_activity
                                WHERE wait_event_type = 'Lock' AND query LIKE ?
                                """, Integer.class, "%" + schema + ".compartment %for%update%")).isEqualTo(2));
                return List.of(first, second);
            });

            var first = requests.getFirst().get(10, TimeUnit.SECONDS);
            var second = requests.getLast().get(10, TimeUnit.SECONDS);
            assertThat(List.of(first, second))
                    .extracting(result -> result.andReturn().getResponse().getStatus())
                    .containsExactlyInAnyOrder(201, 400);

            var rejected = first.andReturn().getResponse().getStatus() == 400 ? first : second;
            rejected.andExpect(jsonPath("$.code").value("INSUFFICIENT_CAPACITY"));
        }

        assertThat(count("ingredient")).isEqualTo(1);
        assertThat(count("compartment_stock")).isEqualTo(1);
        assertThat(count("stock_movement")).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT s.quantity = m.quantity AND s.quantity = 400
                    AND s.quantity <= t.max_capacity AND m.movement_type = 'INBOUND'
                FROM compartment_stock s
                JOIN stock_movement m USING (compartment_id, ingredient_id)
                JOIN ingredient i ON i.id = s.ingredient_id
                JOIN ingredient_type t ON t.id = i.ingredient_type_id
                """, Boolean.class)).isTrue();
    }
}
