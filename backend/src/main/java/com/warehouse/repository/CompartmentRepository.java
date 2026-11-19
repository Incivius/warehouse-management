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
}
