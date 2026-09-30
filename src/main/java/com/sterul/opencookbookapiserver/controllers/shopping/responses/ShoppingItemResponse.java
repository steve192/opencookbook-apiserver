package com.sterul.opencookbookapiserver.controllers.shopping.responses;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.sterul.opencookbookapiserver.controllers.support.DisplayNames;
import com.sterul.opencookbookapiserver.entities.catalogue.Aisle;
import com.sterul.opencookbookapiserver.entities.shopping.ItemStatus;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingItem;

/** @param addedBy a display name; null once that account is gone */
public record ShoppingItemResponse(String id, String name, String spec, Aisle aisle, boolean aisleManual, String icon,
        boolean prioritized, ItemStatus status, Instant boughtAt, Instant addedAt, String addedBy, List<Source> sources,
        boolean deleted, long version) {

    public record Source(String title, LocalDate planDate) {
    }

    public static ShoppingItemResponse of(ShoppingItem item) {
        return new ShoppingItemResponse(item.getId(), item.getName(), item.getSpec(), item.getAisle(),
                item.isAisleManual(), item.getIcon(), item.isPrioritized(), item.getStatus(), item.getBoughtAt(),
                item.getAddedAt(),
                item.getAddedBy() == null ? null : DisplayNames.of(item.getAddedBy()),
                item.getSources().stream().map(source -> new Source(source.getTitle(), source.getPlanDate())).toList(),
                item.isDeleted(), item.getVersion());
    }
}
