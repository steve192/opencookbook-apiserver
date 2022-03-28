package com.sterul.opencookbookapiserver.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngredientNeed {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    private Ingredient ingredient;

    private Float amount;
    private String unit;


    @Embedded
    private ChangeInformationEmbeddable changeInformationEmbeddable;

}
