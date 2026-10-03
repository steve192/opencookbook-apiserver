package com.sterul.opencookbookapiserver.services.instance;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.instance.InstanceSettings;
import com.sterul.opencookbookapiserver.entities.instance.SignupMode;
import com.sterul.opencookbookapiserver.repositories.InstanceSettingsRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
public class InstanceSettingsService {

    private final InstanceSettingsRepository repository;

    public InstanceSettingsService(InstanceSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public SignupMode getSignupMode() {
        return repository.findById(InstanceSettings.ROW_ID).orElseThrow(InstanceSettingsService::missing).getSignupMode();
    }

    public SignupMode setSignupMode(SignupMode signupMode) {
        log.info("Signup mode is now {}", signupMode);
        var settings = lockedRow();
        settings.setSignupMode(signupMode);
        return repository.save(settings).getSignupMode();
    }

    /** Held until the transaction ends, so whoever holds it changes the instance alone. */
    @Transactional(propagation = Propagation.MANDATORY)
    InstanceSettings lock() {
        return lockedRow();
    }

    private InstanceSettings lockedRow() {
        return repository.findLockedById(InstanceSettings.ROW_ID).orElseThrow(InstanceSettingsService::missing);
    }

    private static IllegalStateException missing() {
        return new IllegalStateException("The instance_settings row is missing; the migrations insert it");
    }
}
