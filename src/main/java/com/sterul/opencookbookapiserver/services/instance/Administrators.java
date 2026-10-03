package com.sterul.opencookbookapiserver.services.instance;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.account.Role;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.exceptions.LastAdministratorException;

/**
 * The administrators who can sign in. Without one the setup is open to anybody, so the count is
 * only ever acted on under the {@code instance_settings} row lock that the setup takes as well.
 */
@Component
public class Administrators {

    private final UserRepository users;
    private final InstanceSettingsService settings;

    public Administrators(UserRepository users, InstanceSettingsService settings) {
        this.users = users;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public boolean isSetUp() {
        return activeCount() > 0;
    }

    /**
     * Only the admin panel gives the role back, so losing the last administrator is final. Counts
     * under the lock, so two administrators removing each other cannot both succeed.
     *
     * @param staysAdministrator whether the account is still an active administrator afterwards
     * @throws LastAdministratorException when this would leave nobody
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireOneRemains(CookpalUser user, boolean staysAdministrator) {
        if (staysAdministrator || user.getRoles() != Role.ADMIN || !user.isActivated()) {
            return;
        }
        settings.lock();
        if (activeCount() <= 1) {
            throw new LastAdministratorException();
        }
    }

    private long activeCount() {
        return users.countByRolesAndActivated(Role.ADMIN, true);
    }
}
