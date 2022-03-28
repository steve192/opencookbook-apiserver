package com.sterul.opencookbookapiserver.entities.recipe;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sterul.opencookbookapiserver.entities.ChangeInformationEmbeddable;
import com.sterul.opencookbookapiserver.entities.account.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RecipeGroup {
    @Id
    @GeneratedValue
    private Long id;

    private String title;


    @Embedded
    private ChangeInformationEmbeddable changeInformationEmbeddable;

    @ManyToOne
    @JsonIgnore
    private User owner;

}
