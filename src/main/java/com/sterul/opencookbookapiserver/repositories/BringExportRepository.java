package com.sterul.opencookbookapiserver.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.entities.BringExport;

public interface BringExportRepository extends JpaRepository<BringExport, String> {

}