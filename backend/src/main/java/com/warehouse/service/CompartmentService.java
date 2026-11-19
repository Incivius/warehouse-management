package com.warehouse.service;

import com.warehouse.dto.response.CompartmentAvailabilityResponse;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class CompartmentService {

    private final CompartmentRepository compartmentRepository;
    private final CompartmentStockRepository compartmentStockRepository;
    private final IngredientTypeService ingredientTypeService;

    public CompartmentService(
            CompartmentRepository compartmentRepository,
            CompartmentStockRepository compartmentStockRepository,
            IngredientTypeService ingredientTypeService
    ) {
        this.compartmentRepository = compartmentRepository;
        this.compartmentStockRepository = compartmentStockRepository;
        this.ingredientTypeService = ingredientTypeService;
    }

    @Transactional(readOnly = true)
    public List<CompartmentAvailabilityResponse> findAvailable(String typeName, BigDecimal quantity) {
        return findAvailable(ingredientTypeService.findByName(typeName), quantity);
    }

    private List<CompartmentAvailabilityResponse> findAvailable(IngredientType type, BigDecimal quantity) {
        LocalDate today = LocalDate.now();
        List<CompartmentAvailabilityResponse> available = new ArrayList<>();

        for (var compartment : compartmentRepository.findBalances()) {
            if (isBlocked(compartment.getStatus()) || typeRestriction(
                    compartment.getStatus(), compartment.getCurrentTypeId(),
                    compartment.getLastMovementAt(), type.getId(), today) != null) {
                continue;
            }
            BigDecimal freeCapacity = remainingCapacity(type, compartment.getQuantity());
            if (freeCapacity.compareTo(quantity) >= 0) {
                available.add(new CompartmentAvailabilityResponse(
                        compartment.getId(), compartment.getCode(), freeCapacity, type.getUnitOfMeasure()));
            }
        }

        return available;
    }

    @Transactional(readOnly = true)
    public List<CompartmentAvailabilityResponse> findAvailableForSale(String typeName) {
        IngredientType type = ingredientTypeService.findByName(typeName);
        return compartmentRepository.findAvailableForSale(type.getId());
    }

    @Transactional
    public Compartment validateAndLock(
            Long compartmentId,
            IngredientType ingredientType,
            BigDecimal quantity
    ) {
        Compartment compartment = lockById(compartmentId);
        ErrorCode restriction = inboundRestriction(compartment, ingredientType, quantity);
        if (restriction != null) {
            throw new BusinessException(restriction);
        }
        return compartment;
    }

    @Transactional
    public Compartment selectAndLock(IngredientType type, BigDecimal quantity) {
        for (var candidate : findAvailable(type, quantity)) {
            Compartment compartment = compartmentRepository.findByIdForUpdate(candidate.id()).orElse(null);
            if (compartment != null && !isBlocked(compartment.getStatus())
                    && inboundRestriction(compartment, type, quantity) == null) {
                return compartment;
            }
        }
        throw new BusinessException(ErrorCode.INSUFFICIENT_CAPACITY);
    }

    private ErrorCode inboundRestriction(Compartment compartment, IngredientType ingredientType, BigDecimal quantity) {
        IngredientType currentType = compartment.getCurrentType();
        ErrorCode restriction = typeRestriction(
                compartment.getStatus(), currentType == null ? null : currentType.getId(),
                compartment.getLastMovementAt(), ingredientType.getId(), LocalDate.now());
        if (restriction != null) {
            return restriction;
        }

        BigDecimal currentQuantity = compartmentStockRepository.sumQuantityByCompartmentId(compartment.getId());
        if (remainingCapacity(ingredientType, currentQuantity).compareTo(quantity) < 0) {
            return ErrorCode.INSUFFICIENT_CAPACITY;
        }

        return null;
    }

    Compartment lockById(Long compartmentId) {
        Compartment compartment = compartmentRepository
                .findByIdForUpdate(compartmentId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.COMPARTMENT_NOT_FOUND
                ));

        if (isBlocked(compartment.getStatus())) {
            throw new BusinessException(ErrorCode.COMPARTMENT_TEMPORARILY_BLOCKED);
        }
        return compartment;
    }

    private boolean isBlocked(CompartmentStatus status) {
        return status == CompartmentStatus.BLOCKED;
    }

    private ErrorCode typeRestriction(
            CompartmentStatus status,
            Long currentTypeId,
            LocalDateTime lastMovementAt,
            Long requestedTypeId,
            LocalDate today
    ) {
        if (currentTypeId == null || currentTypeId.equals(requestedTypeId)) {
            return null;
        }
        if (status == CompartmentStatus.OCCUPIED) {
            return ErrorCode.INCOMPATIBLE_INGREDIENT_TYPE;
        }
        if (status == CompartmentStatus.EMPTY && lastMovementAt != null
                && !today.isAfter(lastMovementAt.toLocalDate())) {
            return ErrorCode.COMPARTMENT_TEMPORARILY_BLOCKED;
        }
        return null;
    }

    private BigDecimal remainingCapacity(IngredientType ingredientType, BigDecimal currentQuantity) {
        return ingredientType.getMaxCapacity().subtract(currentQuantity);
    }
}
