package com.sterul.opencookbookapiserver.controllers.shopping.responses;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.sterul.opencookbookapiserver.controllers.support.DisplayNames;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.catalogue.Aisle;
import com.sterul.opencookbookapiserver.entities.shopping.ItemChange;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingListEvent;

/**
 * @param actor    a display name; null once that account is gone
 * @param byViewer made by whoever asks, who reads it as "you"
 */
public record ShoppingListEventResponse(Long id, Instant at, ItemChange kind, String name, Aisle aisle, String icon,
        String spec, String previousName, String actor, boolean byViewer, List<Source> sources) {

    public record Source(String title, LocalDate planDate) {
    }

    public static ShoppingListEventResponse of(ShoppingListEvent event, CookpalUser viewer) {
        var actor = event.getActor();
        return new ShoppingListEventResponse(event.getId(), event.getAt(), event.getKind(), event.getName(),
                event.getAisle(), event.getIcon(), event.getSpec(), event.getPreviousName(),
                actor == null ? null : DisplayNames.of(actor),
                actor != null && actor.getUserId().equals(viewer.getUserId()),
                event.getSources().stream().map(source -> new Source(source.getTitle(), source.getPlanDate())).toList());
    }
}
