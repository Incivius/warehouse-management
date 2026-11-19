package com.warehouse.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record StockSnapshotResponse(List<CompartmentDetails> compartimentos) {

    public record CompartmentDetails(
            Long id,
            String codigo,
            String status,
            String tipo,
            BigDecimal capacidadeMaxima,
            String unidadeMedida,
            BigDecimal quantidadeOcupada,
            BigDecimal capacidadeDisponivel,
            BigDecimal percentualOcupacao,
            LocalDateTime ultimaMovimentacao,
            List<IngredientStock> ingredientes
    ) {}

    public record IngredientStock(
            Long estoqueId,
            Long ingredienteId,
            String nome,
            String tipo,
            BigDecimal quantidade,
            String unidadeMedida
    ) {}
}
