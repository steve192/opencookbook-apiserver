package com.sterul.opencookbookapiserver.entities.nutrition;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Per 100 g, following EU labelling definitions; null where unknown. */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NutrientValues {
    private Float energyKcal;
    private Float energyKj;
    private Float fat;
    private Float saturatedFat;
    private Float carbohydrates;
    private Float sugar;
    private Float fibre;
    private Float protein;
    private Float salt;
}
