package com.sterul.opencookbookapiserver.entities.shopping;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.sterul.opencookbookapiserver.entities.AuditableEntity;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.catalogue.Aisle;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** One name on one list. Deleting leaves a tombstone, so devices that synced earlier hear of it. */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class ShoppingItem extends AuditableEntity {

    public static final String SPEC_SEPARATOR = " + ";

    @Id
    @EqualsAndHashCode.Include
    private String id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ShoppingList list;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String nameKey;

    private String spec;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Aisle aisle;

    /** Chosen by a person, so it is never re-derived from the name. */
    private boolean aisleManual;

    private String icon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private CookpalUser addedBy;

    private Instant boughtAt;

    private long version;

    private boolean deleted;

    @ElementCollection
    @CollectionTable(name = "shopping_item_source", joinColumns = @JoinColumn(name = "shopping_item_id"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    @Builder.Default
    private List<ItemSource> sources = new ArrayList<>();

    public boolean isActive() {
        return !deleted && status == ItemStatus.ACTIVE;
    }

    /** Adding a name that is already on the list asks for more of it. */
    public void addMore(String moreSpec, List<ItemSource> moreSources) {
        spec = joinSpecs(spec, moreSpec);
        sources.addAll(moreSources);
    }

    /** Adding a recently bought name puts it back with only what is asked for now. */
    public void reactivate(String newSpec, List<ItemSource> newSources) {
        status = ItemStatus.ACTIVE;
        boughtAt = null;
        spec = blankToNull(newSpec);
        sources.clear();
        sources.addAll(newSources);
    }

    public void replaceSpec(String newSpec) {
        spec = blankToNull(newSpec);
    }

    public void rename(String newName, String newNameKey) {
        name = newName;
        nameKey = newNameKey;
    }

    public void buy(Instant at) {
        status = ItemStatus.BOUGHT;
        boughtAt = at;
    }

    /** @param shownIcon null shows the aisle's icon */
    public void place(Aisle derived, String shownIcon) {
        aisle = derived;
        icon = shownIcon;
    }

    public void placeManually(Aisle chosen) {
        aisle = chosen;
        aisleManual = true;
    }

    public void delete() {
        deleted = true;
    }

    private static String joinSpecs(String first, String second) {
        if (isBlank(first)) {
            return blankToNull(second);
        }
        return isBlank(second) ? first : first + SPEC_SEPARATOR + second;
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.strip();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
