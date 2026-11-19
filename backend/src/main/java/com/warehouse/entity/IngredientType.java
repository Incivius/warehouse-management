package com.warehouse.entity;

import com.warehouse.enums.UnitOfMeasure;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "ingredient_type")
@Getter
@Setter
@NoArgsConstructor
public class IngredientType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "max_capacity", nullable = false, precision = 12, scale = 3)
    private BigDecimal maxCapacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_of_measure", nullable = false, length = 10)
    private UnitOfMeasure unitOfMeasure;
}