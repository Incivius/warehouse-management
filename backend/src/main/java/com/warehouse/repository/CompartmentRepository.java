package com.warehouse.repository;

import com.warehouse.dto.response.CompartmentAvailabilityResponse;
import com.warehouse.entity.Compartment;
import com.warehouse.enums.CompartmentStatus;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CompartmentRepository
        extends JpaRepository<Compartment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Compartment c WHERE c.id = :id")
    Optional<Compartment> findByIdForUpdate(@Param("id") Long id);

    @Query("""
        SELECT c.id AS id, c.code AS code, c.status AS status, t.id AS currentTypeId,
            c.lastMovementAt AS lastMovementAt, COALESCE(SUM(s.quantity), 0) AS quantity
        FROM Compartment c
        LEFT JOIN c.currentType t
        LEFT JOIN CompartmentStock s ON s.compartment = c
        GROUP BY c.id, c.code, c.status, t.id, c.lastMovementAt
        ORDER BY c.code
    """)
    List<CompartmentBalance> findBalances();

    @Query(value = """
        SELECT c.id AS "compartmentId", c.code AS "compartmentCode", c.status AS status,
            latest.created_at AS "lastMovement", s.id AS "stockId",
            s.quantity AS quantity, i.id AS "ingredientId", i.name AS "ingredientName",
            t.name AS "ingredientType",
            COALESCE(t.max_capacity, dry.max_capacity) AS capacity,
            COALESCE(t.unit_of_measure, dry.unit_of_measure) AS unit
        FROM compartment c
        LEFT JOIN (
            SELECT compartment_id, MAX(created_at) AS created_at
            FROM stock_movement
            GROUP BY compartment_id
        ) latest ON latest.compartment_id = c.id
        LEFT JOIN compartment_stock s ON s.compartment_id = c.id AND s.quantity > 0
        LEFT JOIN ingredient i ON i.id = s.ingredient_id
        LEFT JOIN ingredient_type t ON t.id = i.ingredient_type_id
        LEFT JOIN ingredient_type dry ON dry.name = 'DRY'
        ORDER BY c.code, i.id
    """, nativeQuery = true)
    List<StockSnapshotRow> findStockSnapshot();

    @Query("""
        SELECT new com.warehouse.dto.response.CompartmentAvailabilityResponse(
            c.id, c.code, SUM(s.quantity), t.unitOfMeasure
        )
        FROM CompartmentStock s
        JOIN s.compartment c
        JOIN s.ingredient i
        JOIN i.ingredientType t
        WHERE t.id = :typeId
        GROUP BY c.id, c.code, t.unitOfMeasure
        HAVING SUM(s.quantity) > 0
        ORDER BY c.code
    """)
    List<CompartmentAvailabilityResponse> findAvailableForSale(@Param("typeId") Long typeId);

    interface CompartmentBalance {
        Long getId();
        String getCode();
        CompartmentStatus getStatus();
        Long getCurrentTypeId();
        LocalDateTime getLastMovementAt();
        BigDecimal getQuantity();
    }

    interface StockSnapshotRow {
        Long getCompartmentId();
        String getCompartmentCode();
        String getStatus();
        LocalDateTime getLastMovement();
        Long getStockId();
        BigDecimal getQuantity();
        Long getIngredientId();
        String getIngredientName();
        String getIngredientType();
        BigDecimal getCapacity();
        String getUnit();
    }
}
