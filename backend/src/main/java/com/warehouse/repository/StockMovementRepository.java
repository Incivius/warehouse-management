package com.warehouse.repository;

import com.warehouse.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository
        extends JpaRepository<StockMovement, Long> {

    @Override
    @EntityGraph(attributePaths = {"compartment", "ingredient.ingredientType", "user"})
    Page<StockMovement> findAll(Pageable pageable);
}
