
package com.warehouse.repository;

import com.warehouse.dto.response.IngredientResponse;
import com.warehouse.entity.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    String NORMALIZED_INPUT_NAME = "lower(btrim(regexp_replace(:name, '[[:space:]]+', ' ', 'g')))";

    @Query(value = """
        SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(
            'warehouse:ingredient:' || current_schema() || ':' || CAST(:typeId AS text)
                || ':' ||
        """ + NORMALIZED_INPUT_NAME + ", 0))", nativeQuery = true)
    int lockIdentity(@Param("typeId") Long typeId, @Param("name") String name);

    @Query(value = """
        SELECT i.* FROM ingredient i
        WHERE i.ingredient_type_id = :typeId
            AND lower(btrim(regexp_replace(i.name, '[[:space:]]+', ' ', 'g'))) =
        """ + NORMALIZED_INPUT_NAME + " " + """
        ORDER BY i.id
        LIMIT 1
    """, nativeQuery = true)
    Optional<Ingredient> findByIdentity(@Param("typeId") Long typeId, @Param("name") String name);

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
