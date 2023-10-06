package com.sterul.opencookbookapiserver.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.sterul.opencookbookapiserver.entities.IngredientNutritionalInfo;
import com.sterul.opencookbookapiserver.repositories.IngredientNutritionalInfoRepository;

@Service
public class IngredientNutritionalInfoService {

    private final IngredientNutritionalInfoRepository ingredientNutritionalInfoRepository;

    public IngredientNutritionalInfoService(
            IngredientNutritionalInfoRepository ingredientNutritionalInfoRepository) {
        this.ingredientNutritionalInfoRepository = ingredientNutritionalInfoRepository;
    }

    public IngredientNutritionalInfo addNutritionalInfo(IngredientNutritionalInfo info) {
        return ingredientNutritionalInfoRepository.save(info);
    }

    public List<IngredientNutritionalInfo> getAll() {
        return ingredientNutritionalInfoRepository.findAll();
    }
}
