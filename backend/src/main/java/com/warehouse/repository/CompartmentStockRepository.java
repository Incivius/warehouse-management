package com.warehouse.repository;

import com.warehouse.dto.response.IngredientVolumeResponse;
import com.warehouse.entity.CompartmentStock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CompartmentStockRepository
        extends JpaRepository<CompartmentStock, Long> {

    @Query("""
        SELECT COALESCE(SUM(cs.quantity), 0)
        FROM CompartmentStock cs
        WHERE cs.compartment.id = :compartmentId
    """)
    BigDecimal sumQuantityByCompartmentId(
            @Param("compartmentId") Long compartmentId
    );

    @Query("""
        SELECT new com.warehouse.dto.response.IngredientVolumeResponse(
            t.name, COALESCE(SUM(s.quantity), 0), CAST(t.unitOfMeasure AS String)
        )
        FROM IngredientType t
        LEFT JOIN Ingredient i ON i.ingredientType = t
        LEFT JOIN CompartmentStock s ON s.ingredient = i
        GROUP BY t.name, t.unitOfMeasure
        ORDER BY t.name
    """)
    List<IngredientVolumeResponse> sumQuantityByIngredientType();

    Optional<CompartmentStock> findByCompartmentIdAndIngredientId(
            Long compartmentId,
            Long ingredientId
    );
}
