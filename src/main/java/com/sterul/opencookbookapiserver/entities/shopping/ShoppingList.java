package com.sterul.opencookbookapiserver.entities.shopping;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.sterul.opencookbookapiserver.entities.AuditableEntity;
import com.sterul.opencookbookapiserver.entities.ScopedEntity;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.household.Household;

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

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class ShoppingList extends AuditableEntity implements ScopedEntity {

    @Id
    @SequenceGenerator(name = "shopping_list_seq", sequenceName = "shopping_list_seq", allocationSize = 1)
    @GeneratedValue(generator = "shopping_list_seq")
    @EqualsAndHashCode.Include
    private Long id;

    /** Null for an unrenamed default list, which the app names in its own language. */
    @Column(length = 64)
    private String name;

    /** Exactly one of these is set: a list is a person's own or a household's. */
    @ManyToOne
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CookpalUser owner;

    @ManyToOne
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Household household;

    private boolean defaultList;

    private long version;

    private long purgedVersion;

    /** Every item change takes the next version, so a device can ask for what changed since. */
    public long nextVersion() {
        return ++version;
    }
}
