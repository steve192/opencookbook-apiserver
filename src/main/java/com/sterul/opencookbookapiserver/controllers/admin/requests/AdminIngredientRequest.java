package com.sterul.opencookbookapiserver.controllers.admin.requests;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminIngredientRequest {

    @NotBlank
    private String name;

    private String additionalInfo;
    private List<AlternativeNameRequest> alternativeNames;
    private Float nutrientsEnergy;
    private Float nutrientsFat;
    private Float nutrientsSaturatedFat;
    private Float nutrientsCarbohydrates;
    private Float nutrientsSugar;
    private Float nutrientsProtein;
    private Float nutrientsSalt;

    /** One name the same ingredient also goes by, in one language. */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AlternativeNameRequest {
        private Long id;
        private String languageIsoCode;
        private String alternativeName;
    }
}
