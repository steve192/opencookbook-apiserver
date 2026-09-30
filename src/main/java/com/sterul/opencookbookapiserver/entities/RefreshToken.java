package com.sterul.opencookbookapiserver.entities;

import java.time.Instant;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** One token of a sign in. */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class RefreshToken extends AuditableEntity {

    @Id
    @SequenceGenerator(name = "refresh_token_seq", sequenceName = "refresh_token_seq", allocationSize = 1)
    @GeneratedValue(generator = "refresh_token_seq")
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CookpalUser owner;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** Shared by every token handed out since one sign in, so that one sign in can be ended as a whole. */
    @Column(nullable = false, length = 36)
    private String sessionId;

    /** When the password was entered for this sign in; null when it was not, as after an activation link. */
    private Instant passwordAt;

    /** Moves forward with every use, so a sign in ends once it has not been used for a while. */
    @Column(nullable = false)
    private Instant validUntil;

    /** When it was exchanged for a successor; presenting it again after that ends the sign in. */
    private Instant replacedAt;
}
