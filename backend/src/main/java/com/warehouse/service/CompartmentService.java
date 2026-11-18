package com.warehouse.service;

import com.warehouse.entity.Compartment;
import com.warehouse.entity.IngredientType;
import com.warehouse.enums.CompartmentStatus;
import com.warehouse.exception.BusinessException;
import com.warehouse.exception.ErrorCode;
import com.warehouse.repository.CompartmentRepository;
import com.warehouse.repository.CompartmentStockRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class CompartmentService {

    private final CompartmentRepository compartmentRepository;
    private final CompartmentStockRepository compartmentStockRepository;

    public CompartmentService(
            CompartmentRepository compartmentRepository,
            CompartmentStockRepository compartmentStockRepository
    ) {
        this.compartmentRepository = compartmentRepository;
        this.compartmentStockRepository = compartmentStockRepository;
    }

    @Transactional
    public Compartment validateAndLock(
            Long compartmentId,
            IngredientType ingredientType,
            BigDecimal quantity
    ) {
        Compartment compartment = compartmentRepository
                .findByIdForUpdate(compartmentId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.COMPARTMENT_NOT_FOUND
                ));

        validateStatus(compartment);
        validateIngredientType(compartment, ingredientType);
        validateTemporalRule(compartment, ingredientType);
        validateCapacity(compartment, ingredientType, quantity);

        return compartment;
    }

    private void validateStatus(Compartment compartment) {
        if (compartment.getStatus() == CompartmentStatus.BLOCKED) {
            throw new BusinessException(
                    ErrorCode.COMPARTMENT_TEMPORARILY_BLOCKED
            );
        }
    }

    private void validateIngredientType(
            Compartment compartment,
            IngredientType ingredientType
    ) {
        if (compartment.getCurrentType() != null
                && compartment.getStatus() == CompartmentStatus.OCCUPIED
                && !compartment.getCurrentType().getId()
                    .equals(ingredientType.getId())) {

            throw new BusinessException(
                    ErrorCode.INCOMPATIBLE_INGREDIENT_TYPE
            );
        }
    }

    private void validateTemporalRule(
            Compartment compartment,
            IngredientType ingredientType
    ) {
        if (compartment.getStatus() == CompartmentStatus.EMPTY
                && compartment.getCurrentType() != null
                && !compartment.getCurrentType().getId()
                    .equals(ingredientType.getId())
                && compartment.getLastMovementAt() != null
                && compartment.getLastMovementAt()
                    .toLocalDate().isEqual(LocalDate.now())) {

            throw new BusinessException(
                    ErrorCode.COMPARTMENT_TEMPORARILY_BLOCKED
            );
        }
    }

    private void validateCapacity(
            Compartment compartment,
            IngredientType ingredientType,
            BigDecimal quantity
    ) {
        BigDecimal currentQuantity =
                compartmentStockRepository.sumQuantityByCompartmentId(
                        compartment.getId()
                );

        BigDecimal availableCapacity =
                ingredientType.getMaxCapacity().subtract(currentQuantity);

        if (quantity.compareTo(availableCapacity) > 0) {
            throw new BusinessException(
                    ErrorCode.INSUFFICIENT_CAPACITY
            );
        }
    }
}