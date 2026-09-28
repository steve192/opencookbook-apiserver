package com.sterul.opencookbookapiserver.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.entities.catalogue.CatalogueDatasetImport;

public interface CatalogueDatasetImportRepository extends JpaRepository<CatalogueDatasetImport, String> {

    List<CatalogueDatasetImport> findAllByOrderByStartedAtDesc();
}
