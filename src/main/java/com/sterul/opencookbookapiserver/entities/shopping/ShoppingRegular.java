package com.sterul.opencookbookapiserver.entities.shopping;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.sterul.opencookbookapiserver.entities.catalogue.Aisle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** What a list learned from buying one name: whether it does so at a rhythm, and when to suggest it. */
@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShoppingRegular {

    @Id
    @SequenceGenerator(name = "shopping_regular_seq", sequenceName = "shopping_regular_seq", allocationSize = 1)
    @GeneratedValue(generator = "shopping_regular_seq")
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ShoppingList list;

    @Column(nullable = false)
    private String nameKey;

    /** As it was last bought. */
    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Aisle aisle;

    private String icon;

    /** Null while the purchases make no rhythm. */
    private Long rhythmSeconds;

    private Instant lastBoughtAt;

    /** When its suggestion is first shown; "not now" moves it. Null while muted or without a rhythm. */
    private Instant suggestAt;

    /** Somebody chose not to have it suggested. */
    private boolean muted;

    public Optional<Rhythm> rhythm() {
        return Optional.ofNullable(rhythmSeconds).map(seconds -> new Rhythm(Duration.ofSeconds(seconds), lastBoughtAt));
    }

    /** When a device shows the suggestion, by its own clock; none while muted or without a rhythm. */
    public List<Rhythm.Window> windows() {
        return suggestAt == null ? List.of() : rhythm().map(rhythm -> rhythm.windowsFrom(suggestAt)).orElse(List.of());
    }

    /** From the name's purchases, after one was recorded or taken back; the suggestion follows the new rhythm. */
    public void learn(List<Instant> purchases) {
        var learned = Rhythm.of(purchases);
        rhythmSeconds = learned.map(rhythm -> rhythm.every().toSeconds()).orElse(null);
        lastBoughtAt = purchases.stream().max(Comparator.naturalOrder()).orElse(null);
        suggestAt = muted ? null : learned.map(Rhythm::suggestFrom).orElse(null);
    }

    public void lastSeenAs(String newName, Aisle newAisle, String newIcon) {
        name = newName;
        aisle = newAisle;
        icon = newIcon;
    }

    /** Suggested again half a rhythm later. */
    public void notNow(Instant now) {
        suggestAt = muted ? null : rhythm().map(rhythm -> now.plus(rhythm.snooze())).orElse(null);
    }

    public void mute(boolean mute) {
        muted = mute;
        suggestAt = mute ? null : rhythm().map(Rhythm::suggestFrom).orElse(null);
    }
}
