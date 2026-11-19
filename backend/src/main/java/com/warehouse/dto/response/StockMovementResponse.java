package com.warehouse.dto.response;

import com.warehouse.enums.MovementType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockMovementResponse(
        Long id,
        MovementType operacao,
        Long ingredienteId,
        String ingrediente,
        String tipo,
        BigDecimal quantidade,
        Long compartimentoId,
        String compartimento,
        String responsavel,
        LocalDateTime dataHora
) {}
