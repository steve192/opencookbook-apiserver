package com.sterul.opencookbookapiserver.services.instance;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.account.Role;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.services.UserService;

import lombok.extern.slf4j.Slf4j;

/** An instance is set up once an activated administrator exists; until then the first visitor creates one. */
@Service
@Transactional
@Slf4j
public class SetupService {

    private final Administrators administrators;
    private final InstanceSettingsService settings;
    private final UserService userService;

    public SetupService(Administrators administrators, InstanceSettingsService settings, UserService userService) {
        this.administrators = administrators;
        this.settings = settings;
        this.userService = userService;
    }

    public void requireSetUp() {
        if (!administrators.isSetUp()) {
            throw new ApiException(ApiErrorCode.SETUP_REQUIRED);
        }
    }

    /** Checked again under the settings row lock, so two first visitors cannot both become the administrator. */
    public CookpalUser createFirstAdministrator(String emailAddress, String password) {
        requireNotSetUp();
        settings.lock();
        requireNotSetUp();
        log.info("Setting the instance up with administrator {}", emailAddress);
        return userService.createUser(emailAddress, password, true, Role.ADMIN);
    }

    private void requireNotSetUp() {
        if (administrators.isSetUp()) {
            throw new ApiException(ApiErrorCode.SETUP_COMPLETED);
        }
    }
}
