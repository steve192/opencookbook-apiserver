package com.sterul.opencookbookapiserver.entities.sharing;

import java.time.Instant;

import org.hibernate.annotations.UuidGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sterul.opencookbookapiserver.entities.AuditableEntity;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Share extends AuditableEntity {

    @Id
    @UuidGenerator
    private String id;

    @JsonIgnore
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private CookpalUser owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShareResourceType resourceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShareVisibility visibility;

    /** Set exactly when {@link #resourceType} is {@link ShareResourceType#RECIPE}. 
     * has to be refactored if sharing can also share other things
    */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    private Recipe recipe;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private long accessCount;

    public boolean hasExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
