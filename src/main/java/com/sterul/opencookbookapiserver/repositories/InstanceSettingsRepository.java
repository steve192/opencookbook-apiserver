package com.sterul.opencookbookapiserver.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.sterul.opencookbookapiserver.entities.instance.InstanceSettings;

import jakarta.persistence.LockModeType;

public interface InstanceSettingsRepository extends JpaRepository<InstanceSettings, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InstanceSettings> findLockedById(Long id);
}
