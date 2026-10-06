package com.sterul.opencookbookapiserver.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.account.Role;

import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<CookpalUser, Long> {

    public CookpalUser findByEmailAddress(String emailAddress);
    public Boolean existsByEmailAddress(String emailAddress);

    long countByRolesAndActivated(Role roles, boolean activated);

    @Modifying
    @Query("""
            UPDATE CookpalUser u SET u.lastSignInAt = :at, u.lastActiveAt = :at,
                u.inactivityNotices = 0, u.lastInactivityNoticeAt = NULL
            WHERE u.userId = :id""")
    int recordSignIn(Long id, Instant at);

    /** Conditional, so frequent use writes once per precision span. */
    @Modifying
    @Query("""
            UPDATE CookpalUser u SET u.lastActiveAt = :at, u.inactivityNotices = 0, u.lastInactivityNoticeAt = NULL
            WHERE u.userId = :id AND (u.lastActiveAt IS NULL OR u.lastActiveAt < :staleBefore)""")
    int recordUse(Long id, Instant at, Instant staleBefore);

    @Modifying
    @Query("UPDATE CookpalUser u SET u.inactivityNotices = :notices, u.lastInactivityNoticeAt = :at WHERE u.userId = :id")
    int recordInactivityNotice(Long id, int notices, Instant at);

    /** Administrators and demo accounts have a role and are never deleted for disuse. */
    @Query("""
            SELECT u.userId FROM CookpalUser u
            WHERE u.roles IS NULL AND COALESCE(u.lastActiveAt, u.createdOn) < :activeBefore""")
    List<Long> findIdsOfRolelessUsersInactiveSince(Instant activeBefore);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM CookpalUser u WHERE u.userId = :id")
    Optional<CookpalUser> lockById(Long id);
}
