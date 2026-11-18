package com.warehouse.entity;

import com.warehouse.enums.CompartmentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "compartment")
@Getter
@Setter
@NoArgsConstructor
public class Compartment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompartmentStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_type_id")
    private IngredientType currentType;

    // Data e hora da última saída que deixou o compartimento vazio.
    @Column(name = "last_movement_at")
    private LocalDateTime lastMovementAt;
}
