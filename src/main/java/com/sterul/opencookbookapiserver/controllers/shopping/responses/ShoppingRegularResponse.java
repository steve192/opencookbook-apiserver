package com.sterul.opencookbookapiserver.controllers.shopping.responses;

import java.time.Instant;
import java.util.List;

import com.sterul.opencookbookapiserver.entities.catalogue.Aisle;
import com.sterul.opencookbookapiserver.entities.shopping.Rhythm;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingRegular;

/**
 * @param everyDays null for a muted name whose purchases no longer make a rhythm
 * @param windows   when a device shows the suggestion, by its own clock: shown in one of them unless the name is on
 *                  the list or was bought since {@code lastBoughtAt}; none while muted
 */
public record ShoppingRegularResponse(Long id, String name, Aisle aisle, String icon, Integer everyDays,
        Instant lastBoughtAt, boolean muted, List<Window> windows) {

    public record Window(Instant from, Instant until) {
    }

    public static ShoppingRegularResponse of(ShoppingRegular regular) {
        return new ShoppingRegularResponse(regular.getId(), regular.getName(), regular.getAisle(), regular.getIcon(),
                regular.rhythm().map(Rhythm::everyDays).orElse(null), regular.getLastBoughtAt(), regular.isMuted(),
                regular.windows().stream().map(window -> new Window(window.from(), window.until())).toList());
    }
}
