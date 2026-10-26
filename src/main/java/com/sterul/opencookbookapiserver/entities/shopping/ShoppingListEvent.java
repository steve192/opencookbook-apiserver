package com.sterul.opencookbookapiserver.entities.shopping;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.catalogue.Aisle;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One change a person made to an item of a list: what the history screen shows, and what the list learns its
 * purchases and earlier entries from. Slips are never kept.
 */
@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShoppingListEvent {

    @Id
    @SequenceGenerator(name = "shopping_list_event_seq", sequenceName = "shopping_list_event_seq", allocationSize = 1)
    @GeneratedValue(generator = "shopping_list_event_seq")
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ShoppingList list;

    /** Not a reference: items are purged once deleted, their history is kept. */
    @Column(nullable = false)
    private String itemId;

    /** By the clock of the device that made the change. */
    @Column(nullable = false)
    private Instant at;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemChange kind;

    @Column(nullable = false)
    private String nameKey;

    /** As the item was named after the change. */
    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Aisle aisle;

    private String icon;

    /** What was asked for when added, or the new amount. */
    private String spec;

    /** Before a rename. */
    private String previousName;

    /** Null once that account is gone. */
    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private CookpalUser actor;

    /** The meals an addition was imported for. */
    @ElementCollection
    @CollectionTable(name = "shopping_list_event_source", joinColumns = @JoinColumn(name = "shopping_list_event_id"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    @BatchSize(size = 50)
    @Builder.Default
    private List<ItemSource> sources = new ArrayList<>();
}
