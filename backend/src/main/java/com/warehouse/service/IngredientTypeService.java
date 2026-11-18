package com.warehouse.service;

import com.warehouse.entity.IngredientType;
import com.warehouse.exception.BusinessException;
import com.warehouse.exception.ErrorCode;
import com.warehouse.repository.IngredientTypeRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngredientTypeService {

    private final IngredientTypeRepository ingredientTypeRepository;

    public IngredientTypeService(
            IngredientTypeRepository ingredientTypeRepository
    ) {
        this.ingredientTypeRepository = ingredientTypeRepository;
    }

    @Transactional(readOnly = true)
    public IngredientType findByName(String name) {
        return ingredientTypeRepository.findByName(name)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_REQUEST
                ));
    }
}