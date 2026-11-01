package com.sterul.opencookbookapiserver.entities.catalogue;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A recipe line of a proposal: apply skips it if no longer written as the proposal's name and note, revert moves it back. */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngredientRelinkLine {

    private Long needId;
    private boolean applied;
}
