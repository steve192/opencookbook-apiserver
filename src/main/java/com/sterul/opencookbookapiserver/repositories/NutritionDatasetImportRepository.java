package com.sterul.opencookbookapiserver.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.entities.nutrition.NutritionDatasetImport;

public interface NutritionDatasetImportRepository extends JpaRepository<NutritionDatasetImport, String> {

    List<NutritionDatasetImport> findAllByOrderByStartedAtDesc();
}
