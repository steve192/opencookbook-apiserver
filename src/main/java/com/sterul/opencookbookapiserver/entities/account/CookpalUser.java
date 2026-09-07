package com.sterul.opencookbookapiserver.entities.account;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sterul.opencookbookapiserver.entities.AuditableEntity;

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

    @Override
    public String toString() {
        return getUserId() + " " + getEmailAddress();
    }
}
