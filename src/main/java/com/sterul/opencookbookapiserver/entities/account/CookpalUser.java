package com.sterul.opencookbookapiserver.entities.account;

import java.time.Instant;

import org.hibernate.annotations.DynamicUpdate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sterul.opencookbookapiserver.entities.AuditableEntity;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingProvider;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
// Activity is written by bulk updates; a full-row update of a stale entity would put old values back.
@DynamicUpdate
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CookpalUser extends AuditableEntity {

    @Id
    @SequenceGenerator(name = "cookpal_user_seq", sequenceName = "cookpal_user_seq", allocationSize = 1)
    @GeneratedValue(generator = "cookpal_user_seq")
    private Long userId;
    private String emailAddress;

    @JsonIgnore
    private String passwordHash;

    private boolean activated;

    @Enumerated(EnumType.STRING)
    private Role roles;

    /**
     * The language mails to this account are written in, as a plain language tag ("de", "en").
     *
     * Kept on the account because a mail is usually sent while nobody is holding a request open
     * - and even when one is, the account is the better answer than whatever browser happens to
     * be asking. It is filled in from the client's Accept-Language and updated whenever that
     * changes, so an account that has never said anything leaves it null and gets the default.
     */
    private String language;

    /** Shown to fellow household members instead of the address; null falls back to a masked one. */
    private String displayName;

    /** Whether the first-run screen was completed; clearing the name later does not undo it. */
    private boolean onboarded;

    /** Null until the first shopping import asked. */
    @Enumerated(EnumType.STRING)
    private ShoppingProvider shoppingProvider;

    /** Has signed in with Google. */
    private boolean googleLinked;

    /** Null when not used since signing up. Precise to an hour. */
    private Instant lastActiveAt;

    private Instant lastSignInAt;

    /** Sent since the account was last used. */
    private int inactivityNotices;

    private Instant lastInactivityNoticeAt;

    /** When it last was used, or else when it was created. */
    public Instant lastActiveOrCreatedAt() {
        return lastActiveAt != null ? lastActiveAt : getCreatedOn();
    }

    @Override
    public String toString() {
        return getUserId() + " " + getEmailAddress();
    }
}
