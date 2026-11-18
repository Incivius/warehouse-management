
package com.warehouse.repository;

import com.warehouse.dto.response.IngredientResponse;
import com.warehouse.entity.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    @Query("""
        SELECT new com.warehouse.dto.response.IngredientResponse(i.id, i.name, t.name)
        FROM Ingredient i JOIN i.ingredientType t
        ORDER BY i.id
    """)
    List<IngredientResponse> findAllResponses();

    @Query("""
        SELECT new com.warehouse.dto.response.IngredientResponse(i.id, i.name, t.name)
        FROM Ingredient i JOIN i.ingredientType t
        WHERE i.id = :id
    """)
    Optional<IngredientResponse> findResponseById(@Param("id") Long id);
}
